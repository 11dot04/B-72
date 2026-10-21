import 'dart:io';
import 'dart:math' show max;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import 'package:google_mlkit_object_detection/google_mlkit_object_detection.dart';
import 'package:image_picker/image_picker.dart';
import 'editor.dart';
import 'records.dart';
import 'note_page.dart';
import 'home.dart';
import 'palette.dart';
import 'store.dart';
import 'templates.dart';
import 'type.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(App(store: await Store.load()));
}

const seeds = [Color(0xFF6750A4), Color(0xFF2E7D32), Color(0xFFD84315), Color(0xFF00838F)];
const wide = 1.2; // canvas is 1.2x as wide as the screen

class App extends StatefulWidget {
  const App({super.key, required this.store});
  final Store store;
  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  int seed = 0;
  ThemeMode mode = ThemeMode.system;
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        themeMode: mode,
        theme: ThemeData(useMaterial3: true, colorScheme: ColorScheme.fromSeed(seedColor: seeds[seed])),
        darkTheme: ThemeData(useMaterial3: true, colorScheme: ColorScheme.fromSeed(seedColor: seeds[seed], brightness: Brightness.dark)),
        home: Home(
          store: widget.store,
          onSeed: () => setState(() => seed = (seed + 1) % seeds.length),
          onMode: () => setState(() => mode = ThemeMode.values[(mode.index + 1) % 3]),
        ),
      );
}

enum Kind { photo, text, checklist, bare, quote, stat, info, link, poster, film, track, recipe, book }

TextStyle tStyle(double u, double size, [Color? c]) => sans(u * size, color: c, wght: 650, h: 1.1);
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
      {this.noteId = '', this.order = 0, this.angle = 0, this.hue = 0, this.focal = Rect.zero, this.path, this.title = '', this.titleSize = .05, this.body = '', this.data = const {}});
  final String id, noteId;
  final int order;
  final Kind kind;
  double x, y;
  final double w, aspect, angle, titleSize;
  final int hue;
  final Rect focal; // normalized 0..1 inside the card (photos)
  final String? path;
  final String title, body;
  final Map<String, dynamic> data;
  Lay? _l;
  double _lw = 0;

  bool get isText => kind != Kind.photo;
  List<String> get items => body.split('\n').where((s) => s.trim().isNotEmpty).toList();

  Lay layout(double cw) {
    if (_l != null && _lw == cw) return _l!;
    if (isRec(kind)) {
      _lw = cw;
      return _l = Lay(w * cw * recAspect(kind), 0, 0, 0);
    }
    if (isTpl(kind)) {
      _lw = cw;
      return _l = tplLay(kind, title, body, w * cw, cw);
    }
    final u = cw / wide, wpx = w * cw, o = u * .012, pad = u * .03, inner = wpx - 2 * o - 2 * pad;
    final t = TextPainter(text: TextSpan(text: title, style: tStyle(u, titleSize)), textDirection: TextDirection.ltr, maxLines: kind == Kind.bare ? null : 4)..layout(maxWidth: inner);
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
    if (h > cap && kind != Kind.bare) {
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
  const Board({super.key, required this.store, required this.tag, required this.onSeed});
  final Store store;
  final String tag;
  final VoidCallback onSeed;
  @override
  State<Board> createState() => _BoardState();
}

class _BoardState extends State<Board> {
  late List<NoteCard> cards = widget.store.cardsFor(widget.tag);
  bool showFocal = true, tidied = false;
  NoteCard? dragging;
  Color? photoSeed;
  bool usePhoto = true;
  double sx = 0, sy = 0, lastCw = 400;
  late Color bg;
  final vc = ScrollController();
  ScrollController? hc;

  /// Draw order: photos below, everything else above. Last = top.
  List<NoteCard> get z => [...cards.where((c) => !c.isText), ...cards.where((c) => c.isText)];

  bool ok(NoteCard m, double w) {
    final r = m.rect(w);
    if (r.left < 0 || r.top < 0 || r.right > w) return false;
    // text stays inside the visible band; only photos may bleed off the edges
    if (m.isText && (r.left < w * .083 || r.right > w * .917)) return false;
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

  void persist() {
    for (final c in cards) {
      widget.store.setPlace(widget.tag, c.id, c.x, c.y);
    }
    widget.store.save();
  }

  void loadSeed() => seedFromPhotos(widget.store.photosFor(widget.tag)).then((c) {
        if (mounted) setState(() => photoSeed = c);
      });

  @override
  void initState() {
    super.initState();
    photoSeed = peekSeed(widget.store.photosFor(widget.tag));
    loadSeed();
  }

  void reload() {
    setState(() {
      cards = widget.store.cardsFor(widget.tag);
      tidied = false;
    });
    loadSeed();
  }

  Future<void> open(NoteCard c) async {
    final n = widget.store.notes.firstWhere((n) => n.id == c.noteId);
    await Navigator.push(context, MaterialPageRoute(builder: (_) => NotePage(store: widget.store, note: n)));
    reload();
  }

  Future<void> create() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Editor(store: widget.store, tag: widget.tag)));
    reload();
  }

  @override
  void dispose() {
    vc.dispose();
    hc?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final base = Theme.of(context), sd = usePhoto ? photoSeed : null;
    final t = sd == null ? base : base.copyWith(colorScheme: ColorScheme.fromSeed(seedColor: sd, brightness: base.brightness));
    return Theme(data: t, child: Builder(builder: canvas));
  }

  Widget canvas(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    bg = Color.alphaBlend(cs.primary.withAlpha(18), cs.surface);
    return Scaffold(
      backgroundColor: bg,
      appBar: AppBar(
        title: Text(widget.tag),
        backgroundColor: Colors.transparent,
        scrolledUnderElevation: 0,
        actions: [
          IconButton(tooltip: 'Show subject areas', icon: const Icon(Icons.center_focus_strong), onPressed: () => setState(() => showFocal = !showFocal)),
          IconButton(tooltip: 'Photo palette', icon: Icon(usePhoto ? Icons.palette : Icons.palette_outlined), onPressed: () => setState(() => usePhoto = !usePhoto)),
          IconButton(tooltip: 'Re-tidy', icon: const Icon(Icons.auto_fix_high), onPressed: () => setState(() => tidied = false)),
        ],
      ),
      floatingActionButton: FloatingActionButton(onPressed: create, child: const Icon(Icons.add)),
      body: LayoutBuilder(builder: (context, box) {
        final sw = box.maxWidth, cw = sw * wide;
        lastCw = cw;
        hc ??= ScrollController(initialScrollOffset: (cw - sw) / 2);
        if (!tidied) {
          tidied = true;
          WidgetsBinding.instance.addPostFrameCallback((_) => setState(() {
            tidy(cw);
            persist();
          }));
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
          persist();
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
    if (isRec(c.kind)) return recCard(c.kind, c.title, c.data, c.path, u, cs);
    if (isTpl(c.kind)) {
      final (tb, tf) = tplColors(c.kind, cs);
      return ColoredBox(
        color: tb,
        child: Padding(
          padding: EdgeInsets.all(u * .035),
          child: ClipRect(child: OverflowBox(alignment: Alignment.topLeft, minHeight: 0, maxHeight: double.infinity, child: tplBody(c.kind, c.title, c.body, u, tf, cs, L))),
        ),
      );
    }
    final bgc = bare ? Colors.transparent : isNote ? cs.tertiaryContainer : cs.secondaryContainer;
    final fg = bare ? cs.onSurface : isNote ? cs.onTertiaryContainer : cs.onSecondaryContainer;
    return ColoredBox(
      color: bgc,
      child: Padding(
        padding: EdgeInsets.all(u * .03),
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title, maxLines: bare ? null : 4, overflow: TextOverflow.ellipsis, style: tStyle(u, c.titleSize, fg)),
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
