import 'dart:math';
import 'package:flutter/material.dart';

void main() => runApp(const App());

const seeds = [Color(0xFF6750A4), Color(0xFF2E7D32), Color(0xFFD84315), Color(0xFF00838F)];

class App extends StatefulWidget {
  const App({super.key});
  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  int seed = 0;
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: ThemeData(useMaterial3: true, colorScheme: ColorScheme.fromSeed(seedColor: seeds[seed])),
        home: Board(onSeed: () => setState(() => seed = (seed + 1) % seeds.length)),
      );
}

enum Kind { photo, text, checklist }

/// Positions are stored as fractions of canvas WIDTH, so the layout
/// adapts to any screen size. `aspect` is height / width.
class NoteCard {
  NoteCard(this.id, this.kind, this.x, this.y, this.w, this.aspect,
      {this.angle = 0, this.hue = 0, this.focal = Rect.zero, this.title = '', this.titleSize = .05, this.lines = const []});
  final String id;
  final Kind kind;
  double x, y;
  final double w, aspect, angle, titleSize;
  final int hue;
  final Rect focal; // normalized 0..1 inside the card (photos only)
  final String title;
  final List<String> lines;

  bool get isText => kind != Kind.photo;
  Rect rect(double cw) => Rect.fromLTWH(x * cw, y * cw, w * cw, w * cw * aspect);

  /// Text cards: whole card is protected. Photos: only the subject area.
  Rect protectedRect(double cw) {
    final r = rect(cw);
    if (isText) return r;
    return Rect.fromLTRB(r.left + focal.left * r.width, r.top + focal.top * r.height,
        r.left + focal.right * r.width, r.top + focal.bottom * r.height);
  }
}

List<NoteCard> seedCards() => [
      NoteCard('p1', Kind.photo, .04, .03, .60, .75, angle: -.04, hue: 0, focal: const Rect.fromLTRB(.3, .25, .7, .7)),
      NoteCard('p2', Kind.photo, .40, .36, .52, .80, angle: .05, hue: 1, focal: const Rect.fromLTRB(.25, .2, .75, .65)),
      NoteCard('p3', Kind.photo, .52, .80, .44, .80, angle: -.03, hue: 2, focal: const Rect.fromLTRB(.3, .25, .7, .65)),
      NoteCard('t1', Kind.text, .04, .52, .38, .62, title: 'I have a planter from my ceramics class!', titleSize: .058),
      NoteCard('t2', Kind.text, .04, .78, .46, .55,
          title: 'Care tips', lines: ['Check soil moisture before watering.', 'Avoid overwatering.']),
      NoteCard('c1', Kind.checklist, .62, .04, .34, 1.15,
          title: 'Varieties', lines: ['Snake plant', 'x Spider plant', 'Pothos', 'Monstera', 'ZZ plant']),
    ];

class Board extends StatefulWidget {
  const Board({super.key, required this.onSeed});
  final VoidCallback onSeed;
  @override
  State<Board> createState() => _BoardState();
}

class _BoardState extends State<Board> {
  List<NoteCard> cards = seedCards();
  bool showFocal = true;
  NoteCard? dragging;

  /// Draw order: photos below, text/checklists always above. Last = top.
  List<NoteCard> get z => [...cards.where((c) => !c.isText), ...cards.where((c) => c.isText)];

  /// A position is valid if it is inside the canvas and no card's rect
  /// covers a card below it's protected area (text card or photo subject).
  bool ok(NoteCard m, double w, double h) {
    final r = m.rect(w);
    if (r.left < 0 || r.top < 0 || r.right > w || r.bottom > h) return false;
    final order = z;
    final mi = order.indexOf(m);
    for (var i = 0; i < order.length; i++) {
      final o = order[i];
      if (o == m) continue;
      final up = i > mi ? o : m;
      final low = i > mi ? m : o;
      if (up.rect(w).overlaps(low.protectedRect(w))) return false;
    }
    return true;
  }

  /// On drop, find the nearest valid position (expanding square search).
  void settle(NoteCard m, double w, double h) {
    final ox = m.x, oy = m.y;
    const step = 0.015;
    for (var r = 0; r < 60; r++) {
      final ring = <Offset>[];
      for (var dx = -r; dx <= r; dx++) {
        for (var dy = -r; dy <= r; dy++) {
          if (max(dx.abs(), dy.abs()) == r) ring.add(Offset(dx * step, dy * step));
        }
      }
      ring.sort((a, b) => a.distance.compareTo(b.distance));
      for (final o in ring) {
        m.x = ox + o.dx;
        m.y = oy + o.dy;
        if (ok(m, w, h)) return;
      }
    }
    m.x = ox;
    m.y = oy;
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: Color.alphaBlend(cs.primary.withAlpha(18), cs.surface),
      appBar: AppBar(
        title: const Text('Canvas prototype'),
        backgroundColor: Colors.transparent,
        actions: [
          IconButton(tooltip: 'Show subject areas', icon: const Icon(Icons.center_focus_strong), onPressed: () => setState(() => showFocal = !showFocal)),
          IconButton(tooltip: 'Next palette', icon: const Icon(Icons.palette_outlined), onPressed: widget.onSeed),
          IconButton(tooltip: 'Reset', icon: const Icon(Icons.refresh), onPressed: () => setState(() => cards = seedCards())),
        ],
      ),
      body: LayoutBuilder(builder: (context, box) {
        final w = box.maxWidth, h = box.maxHeight;
        return Stack(children: [for (final c in z) card(c, w, h, cs)]);
      }),
    );
  }

  Widget card(NoteCard c, double w, double h, ColorScheme cs) {
    final r = c.rect(w);
    final isDrag = identical(c, dragging);
    final bad = isDrag && !ok(c, w, h);
    final radius = BorderRadius.circular(w * .05);
    return AnimatedPositioned(
      key: ValueKey(c.id),
      duration: isDrag ? Duration.zero : const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      left: r.left,
      top: r.top,
      width: r.width,
      height: r.height,
      child: GestureDetector(
        onPanStart: (_) => setState(() {
          dragging = c;
          cards.remove(c);
          cards.add(c);
        }),
        onPanUpdate: (d) => setState(() {
          c.x += d.delta.dx / w;
          c.y += d.delta.dy / w;
        }),
        onPanEnd: (_) => setState(() {
          dragging = null;
          settle(c, w, h);
        }),
        onPanCancel: () => setState(() => dragging = null),
        child: Transform.rotate(
          angle: c.angle,
          child: DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: radius,
              border: Border.all(color: bad ? cs.error : Colors.transparent, width: 3),
              boxShadow: const [BoxShadow(blurRadius: 8, color: Colors.black26, offset: Offset(0, 3))],
            ),
            child: ClipRRect(borderRadius: radius, child: body(c, w, cs)),
          ),
        ),
      ),
    );
  }

  Widget body(NoteCard c, double w, ColorScheme cs) {
    if (c.kind == Kind.photo) {
      final pairs = [
        [cs.tertiaryContainer, cs.primaryContainer],
        [cs.primaryContainer, cs.secondaryContainer],
        [cs.secondaryContainer, cs.tertiaryContainer],
      ];
      return LayoutBuilder(builder: (_, b) {
        final f = Rect.fromLTRB(c.focal.left * b.maxWidth, c.focal.top * b.maxHeight, c.focal.right * b.maxWidth, c.focal.bottom * b.maxHeight);
        return Stack(fit: StackFit.expand, children: [
          DecoratedBox(
            decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: pairs[c.hue % 3])),
          ),
          Positioned.fromRect(rect: f, child: FittedBox(child: Icon(Icons.face, color: cs.onPrimaryContainer.withAlpha(150)))),
          if (showFocal) Positioned.fromRect(rect: f, child: DecoratedBox(decoration: BoxDecoration(border: Border.all(color: cs.error, width: 2)))),
        ]);
      });
    }
    final isNote = c.kind == Kind.text;
    final bg = isNote ? cs.tertiaryContainer : cs.secondaryContainer;
    final fg = isNote ? cs.onTertiaryContainer : cs.onSecondaryContainer;
    return ColoredBox(
      color: bg,
      child: Padding(
        padding: EdgeInsets.all(w * .03),
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title, style: TextStyle(fontFamily: 'serif', fontSize: w * c.titleSize, height: 1.15, color: fg)),
            for (final l in c.lines)
              Padding(
                padding: EdgeInsets.only(top: w * .008),
                child: isNote ? Text(l, style: TextStyle(fontSize: w * .034, color: fg)) : checkRow(l, w, fg),
              ),
          ]),
        ),
      ),
    );
  }

  Widget checkRow(String l, double w, Color fg) {
    final done = l.startsWith('x ');
    return Row(children: [
      Icon(done ? Icons.radio_button_checked : Icons.radio_button_unchecked, size: w * .04, color: fg),
      SizedBox(width: w * .015),
      Expanded(
        child: Text(done ? l.substring(2) : l,
            style: TextStyle(fontSize: w * .034, color: fg, decoration: done ? TextDecoration.lineThrough : null)),
      ),
    ]);
  }
}
