import 'dart:io';
import 'dart:math' show max;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:google_mlkit_object_detection/google_mlkit_object_detection.dart';
import 'package:image_picker/image_picker.dart';

void main() => runApp(const App());

const seeds = [Color(0xFF6750A4), Color(0xFF2E7D32), Color(0xFFD84315), Color(0xFF00838F)];
const wide = 1.2; // canvas is 1.2x as wide as the screen

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

enum Kind { photo, text, checklist, bare }

TextStyle tStyle(double u, double size, [Color? c]) => TextStyle(fontFamily: 'serif', fontSize: u * size, height: 1.15, color: c);
TextStyle bStyle(double u, [Color? c]) => TextStyle(fontSize: u * .034, height: 1.25, color: c);

class Lay {
  Lay(this.h, this.lines, this.rows, this.hidden);
  final double h;
  final int lines, rows, hidden; // lines: note body max lines (0 = all); rows/hidden: checklist
}

/// x, y, w are fractions of canvas WIDTH. Photos use `aspect` (h/w);
/// text cards size themselves from content, capped at a square.
class NoteCard {
  NoteCard(this.id, this.kind, this.x, this.y, this.w, this.aspect,
      {this.angle = 0, this.hue = 0, this.focal = Rect.zero, this.path, this.title = '', this.titleSize = .05, this.body = ''});
  final String id;
  final Kind kind;
  double x, y;
  final double w, aspect, angle, titleSize;
  final int hue;
  final Rect focal; // normalized 0..1 inside the card (photos)
  final String? path;
  final String title, body;
  Lay? _l;
  double _lw = 0;

  bool get isText => kind != Kind.photo;
  List<String> get items => body.split('\n').where((s) => s.trim().isNotEmpty).toList();

  Lay layout(double cw) {
    if (_l != null && _lw == cw) return _l!;
    final u = cw / wide, wpx = w * cw, o = u * .012, pad = u * .03, inner = wpx - 2 * o - 2 * pad;
    final t = TextPainter(text: TextSpan(text: title, style: tStyle(u, titleSize)), textDirection: TextDirection.ltr, maxLines: 4)..layout(maxWidth: inner);
    final isList = kind == Kind.checklist;
    final hasBody = kind != Kind.bare && body.trim().isNotEmpty;
    final base = 2 * o + 2 * pad + t.height + (hasBody ? u * .008 : 0);
    double lineH = u * .052, natural = 0;
    if (hasBody && isList) {
      natural = items.length * lineH;
    } else if (hasBody) {
      final b = TextPainter(text: TextSpan(text: body, style: bStyle(u)), textDirection: TextDirection.ltr)..layout(maxWidth: inner);
      lineH = b.preferredLineHeight;
      natural = b.height;
    }
    var h = base + natural, lines = 0, rows = isList ? items.length : 0, hidden = 0;
    final cap = max(wpx, base + lineH); // 1:1 square at max lines
    if (h > cap) {
      h = cap;
      final fit = ((cap - base) / lineH).floor();
      if (isList) {
        rows = max(1, fit - 1);
        hidden = items.length - rows;
      } else {
        lines = max(1, fit);
      }
    }
    _lw = cw;
    return _l = Lay(h, lines, rows, hidden);
  }

  Rect rect(double cw) => Rect.fromLTWH(x * cw, y * cw, w * cw, isText ? layout(cw).h : w * cw * aspect);

  /// Text cards: whole card protected. Photos: only the subject area.
  Rect protectedRect(double cw) {
    final r = rect(cw);
    if (isText) return r;
    return Rect.fromLTRB(r.left + focal.left * r.width, r.top + focal.top * r.height, r.left + focal.right * r.width, r.top + focal.bottom * r.height);
  }
}

List<NoteCard> seedCards() => [
      NoteCard('p1', Kind.photo, .117, .025, .50, .75, angle: -.04, hue: 0, focal: const Rect.fromLTRB(.3, .25, .7, .7)),
      NoteCard('p2', Kind.photo, .417, .30, .433, .80, angle: .05, hue: 1, focal: const Rect.fromLTRB(.25, .2, .75, .65)),
      NoteCard('p3', Kind.photo, .517, .667, .367, .80, angle: -.03, hue: 2, focal: const Rect.fromLTRB(.3, .25, .7, .65)),
      NoteCard('t1', Kind.bare, .117, .45, .30, 0, title: 'I have a planter from my ceramics class!', titleSize: .07),
      NoteCard('t2', Kind.text, .117, .65, .38, 0,
          title: 'Care tips',
          body: 'Check soil moisture before watering. Avoid overwatering by feeling the soil; water when the top inch is dry. Provide adequate drainage, and use pots with drainage holes.'),
      NoteCard('c1', Kind.checklist, .60, .033, .30, 0,
          title: 'Varieties', body: 'Snake plant\nx Spider plant\nPothos\nMonstera\nZZ plant\nFiddle leaf fig\nPeace lily'),
    ];

/// Faces first, then prominent objects, then a centred fallback.
Future<Rect> detectFocus(String path, Size size) async {
  final input = InputImage.fromFilePath(path);
  Rect? u;
  final fd = FaceDetector(options: FaceDetectorOptions(performanceMode: FaceDetectorMode.accurate));
  for (final f in await fd.processImage(input)) {
    u = u == null ? f.boundingBox : u.expandToInclude(f.boundingBox);
  }
  await fd.close();
  if (u == null) {
    final od = ObjectDetector(options: ObjectDetectorOptions(mode: DetectionMode.single, classifyObjects: false, multipleObjects: true));
    for (final o in await od.processImage(input)) {
      u = u == null ? o.boundingBox : u.expandToInclude(o.boundingBox);
    }
    await od.close();
  }
  if (u == null) return const Rect.fromLTRB(.3, .3, .7, .7);
  return Rect.fromLTRB(u.left / size.width, u.top / size.height, u.right / size.width, u.bottom / size.height)
      .inflate(.03)
      .intersect(const Rect.fromLTRB(0, 0, 1, 1));
}

class Board extends StatefulWidget {
  const Board({super.key, required this.onSeed});
  final VoidCallback onSeed;
  @override
  State<Board> createState() => _BoardState();
}

class _BoardState extends State<Board> {
  List<NoteCard> cards = seedCards();
  bool showFocal = true, tidied = false;
  NoteCard? dragging;
  double sx = 0, sy = 0, lastCw = 400;
  late Color bg;
  final vc = ScrollController();
  ScrollController? hc;

  /// Draw order: photos below, everything else above. Last = top.
  List<NoteCard> get z => [...cards.where((c) => !c.isText), ...cards.where((c) => c.isText)];

  bool ok(NoteCard m, double w) {
    final r = m.rect(w);
    if (r.left < 0 || r.top < 0 || r.right > w) return false;
    final order = z;
    final mi = order.indexOf(m);
    for (var i = 0; i < order.length; i++) {
      final o = order[i];
      if (o == m) continue;
      // bare text lives on the plain canvas: nothing may touch it
      if ((m.kind == Kind.bare || o.kind == Kind.bare) && r.overlaps(o.rect(w))) return false;
      final up = i > mi ? o : m, low = i > mi ? m : o;
      if (up.rect(w).overlaps(low.protectedRect(w))) return false;
    }
    return true;
  }

  void settle(NoteCard m, double w) {
    final ox = m.x, oy = m.y;
    const step = 0.015;
    for (var r = 0; r < 70; r++) {
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
        if (ok(m, w)) return;
      }
    }
    m.x = ox;
    m.y = oy;
  }

  void tidy(double cw) {
    for (final c in z) {
      settle(c, cw);
    }
  }

  double get spawnY => (vc.hasClients ? vc.offset : 0) / lastCw + .03;

  void add(NoteCard c) => setState(() {
        cards.add(c);
        settle(c, lastCw);
      });

  Future<void> addPhoto() async {
    final f = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (f == null) return;
    final img = await decodeImageFromList(await f.readAsBytes());
    final ai = img.height / img.width, ac = ai.clamp(.6, 1.4).toDouble();
    var fo = await detectFocus(f.path, Size(img.width.toDouble(), img.height.toDouble()));
    // map the subject box into card coordinates (BoxFit.cover crops)
    if (ai > ac) {
      final v = ac / ai;
      fo = Rect.fromLTRB(fo.left, (fo.top - (1 - v) / 2) / v, fo.right, (fo.bottom - (1 - v) / 2) / v);
    } else if (ai < ac) {
      final h = ai / ac;
      fo = Rect.fromLTRB((fo.left - (1 - h) / 2) / h, fo.top, (fo.right - (1 - h) / 2) / h, fo.bottom);
    }
    fo = fo.intersect(const Rect.fromLTRB(0, 0, 1, 1));
    if (fo.width <= 0 || fo.height <= 0) fo = const Rect.fromLTRB(.3, .3, .7, .7);
    add(NoteCard(DateTime.now().toIso8601String(), Kind.photo, .31, spawnY, .38, ac,
        angle: ((f.path.hashCode % 9) - 4) * .01, focal: fo, path: f.path));
  }

  Future<void> compose(Kind k) async {
    final t = TextEditingController(), b = TextEditingController();
    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: t, decoration: const InputDecoration(labelText: 'Title')),
          if (k != Kind.bare)
            TextField(
              controller: b,
              minLines: 2,
              maxLines: 6,
              decoration: InputDecoration(labelText: k == Kind.checklist ? 'One item per line (start with x if done)' : 'Text'),
            ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Add')),
        ],
      ),
    );
    if (saved != true || (t.text.trim().isEmpty && b.text.trim().isEmpty)) return;
    add(NoteCard(DateTime.now().toIso8601String(), k, k == Kind.bare ? .25 : .32, spawnY, k == Kind.bare ? .5 : .36, 0,
        title: t.text.trim(), titleSize: k == Kind.bare ? .07 : .05, body: b.text.trim()));
  }

  void addMenu() => showModalBottomSheet(
        context: context,
        builder: (_) => SafeArea(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            for (final e in [
              (Icons.photo_outlined, 'Photo', null),
              (Icons.sticky_note_2_outlined, 'Note', Kind.text),
              (Icons.checklist, 'Checklist', Kind.checklist),
              (Icons.title, 'Text on canvas', Kind.bare),
            ])
              ListTile(
                leading: Icon(e.$1),
                title: Text(e.$2),
                onTap: () {
                  Navigator.pop(context);
                  e.$3 == null ? addPhoto() : compose(e.$3!);
                },
              ),
          ]),
        ),
      );

  void open(NoteCard c) => showModalBottomSheet(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (_) => Padding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (c.kind == Kind.photo)
              Flexible(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: c.path != null ? Image.file(File(c.path!)) : const Icon(Icons.face, size: 96),
                ),
              )
            else ...[
              Text(c.title, style: const TextStyle(fontFamily: 'serif', fontSize: 26)),
              const SizedBox(height: 12),
              Flexible(
                child: SingleChildScrollView(
                  child: Text(
                    c.kind == Kind.checklist
                        ? c.items.map((s) => s.startsWith('x ') ? '✓ ${s.substring(2)}' : '○ $s').join('\n')
                        : c.body,
                    style: const TextStyle(fontSize: 16, height: 1.4),
                  ),
                ),
              ),
            ],
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  setState(() => cards.remove(c));
                },
                icon: const Icon(Icons.delete_outline),
                label: const Text('Delete'),
              ),
            ),
          ]),
        ),
      );

  @override
  void dispose() {
    vc.dispose();
    hc?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    bg = Color.alphaBlend(cs.primary.withAlpha(18), cs.surface);
    return Scaffold(
      backgroundColor: bg,
      appBar: AppBar(
        title: const Text('Canvas prototype'),
        backgroundColor: Colors.transparent,
        scrolledUnderElevation: 0,
        actions: [
          IconButton(tooltip: 'Show subject areas', icon: const Icon(Icons.center_focus_strong), onPressed: () => setState(() => showFocal = !showFocal)),
          IconButton(tooltip: 'Next palette', icon: const Icon(Icons.palette_outlined), onPressed: widget.onSeed),
          IconButton(tooltip: 'Reset', icon: const Icon(Icons.refresh), onPressed: () => setState(() {
                cards = seedCards();
                tidied = false;
              })),
        ],
      ),
      floatingActionButton: FloatingActionButton(onPressed: addMenu, child: const Icon(Icons.add)),
      body: LayoutBuilder(builder: (context, box) {
        final sw = box.maxWidth, cw = sw * wide;
        lastCw = cw;
        hc ??= ScrollController(initialScrollOffset: (cw - sw) / 2);
        if (!tidied) {
          tidied = true;
          WidgetsBinding.instance.addPostFrameCallback((_) => setState(() => tidy(cw)));
        }
        final bottom = cards.fold<double>(0, (m, c) => max(m, c.rect(cw).bottom));
        final ch = max(box.maxHeight, bottom + cw * .35);
        return SingleChildScrollView(
          controller: vc,
          child: SingleChildScrollView(
            controller: hc,
            scrollDirection: Axis.horizontal,
            child: SizedBox(width: cw, height: ch, child: Stack(children: [for (final c in z) card(c, cw, cs)])),
          ),
        );
      }),
    );
  }

  Widget card(NoteCard c, double cw, ColorScheme cs) {
    final r = c.rect(cw), u = cw / wide, ring = u * .012, radius = u * .05;
    final isDrag = identical(c, dragging), bad = isDrag && !ok(c, cw), bare = c.kind == Kind.bare;
    // the "negative outline": a ring in the canvas colour around every card
    final ringColor = bare ? (bad ? cs.error.withAlpha(70) : Colors.transparent) : (bad ? cs.error : bg);
    return AnimatedPositioned(
      key: ValueKey(c.id),
      duration: isDrag ? Duration.zero : const Duration(milliseconds: 220),
      curve: Curves.easeOut,
      left: r.left,
      top: r.top,
      width: r.width,
      height: r.height,
      child: GestureDetector(
        onTap: () => open(c),
        onLongPressStart: (_) {
          HapticFeedback.mediumImpact();
          setState(() {
            dragging = c;
            sx = c.x;
            sy = c.y;
            cards.remove(c);
            cards.add(c);
          });
        },
        onLongPressMoveUpdate: (d) => setState(() {
          c.x = sx + d.offsetFromOrigin.dx / cw;
          c.y = sy + d.offsetFromOrigin.dy / cw;
        }),
        onLongPressEnd: (_) => setState(() {
          dragging = null;
          settle(c, cw);
        }),
        onLongPressCancel: () => setState(() => dragging = null),
        child: Transform.rotate(
          angle: c.angle,
          child: DecoratedBox(
            decoration: BoxDecoration(color: ringColor, borderRadius: BorderRadius.circular(radius + ring)),
            child: Padding(
              padding: EdgeInsets.all(ring),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(bare ? 0 : radius),
                child: c.kind == Kind.photo ? photo(c, cs) : textBody(c, cw, cs),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget photo(NoteCard c, ColorScheme cs) {
    final pairs = [
      [cs.tertiaryContainer, cs.primaryContainer],
      [cs.primaryContainer, cs.secondaryContainer],
      [cs.secondaryContainer, cs.tertiaryContainer],
    ];
    return LayoutBuilder(builder: (_, b) {
      final f = Rect.fromLTRB(c.focal.left * b.maxWidth, c.focal.top * b.maxHeight, c.focal.right * b.maxWidth, c.focal.bottom * b.maxHeight);
      return Stack(fit: StackFit.expand, children: [
        if (c.path != null)
          Image.file(File(c.path!), fit: BoxFit.cover, cacheWidth: 800)
        else ...[
          DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: pairs[c.hue % 3]))),
          Positioned.fromRect(rect: f, child: FittedBox(child: Icon(Icons.face, color: cs.onPrimaryContainer.withAlpha(150)))),
        ],
        if (showFocal) Positioned.fromRect(rect: f, child: DecoratedBox(decoration: BoxDecoration(border: Border.all(color: cs.error, width: 2)))),
      ]);
    });
  }

  Widget textBody(NoteCard c, double cw, ColorScheme cs) {
    final u = cw / wide, L = c.layout(cw), bare = c.kind == Kind.bare, isNote = c.kind == Kind.text;
    final bgc = bare ? Colors.transparent : isNote ? cs.tertiaryContainer : cs.secondaryContainer;
    final fg = bare ? cs.onSurface : isNote ? cs.onTertiaryContainer : cs.onSecondaryContainer;
    return ColoredBox(
      color: bgc,
      child: Padding(
        padding: EdgeInsets.all(u * .03),
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title, maxLines: 4, overflow: TextOverflow.ellipsis, style: tStyle(u, c.titleSize, fg)),
            if (!bare && c.body.trim().isNotEmpty) SizedBox(height: u * .008),
            if (c.kind == Kind.checklist) ...[
              for (final l in c.items.take(L.rows)) checkRow(l, u, fg),
              if (L.hidden > 0) Text('+${L.hidden} more', style: bStyle(u, fg)),
            ] else if (isNote)
              Text(c.body, maxLines: L.lines == 0 ? null : L.lines, overflow: TextOverflow.ellipsis, style: bStyle(u, fg)),
          ]),
        ),
      ),
    );
  }

  Widget checkRow(String l, double u, Color fg) {
    final done = l.startsWith('x ');
    return Padding(
      padding: EdgeInsets.only(top: u * .008),
      child: Row(children: [
        Icon(done ? Icons.radio_button_checked : Icons.radio_button_unchecked, size: u * .04, color: fg),
        SizedBox(width: u * .015),
        Expanded(
          child: Text(done ? l.substring(2) : l,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: bStyle(u, fg).copyWith(decoration: done ? TextDecoration.lineThrough : null)),
        ),
      ]),
    );
  }
}
