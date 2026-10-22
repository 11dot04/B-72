import 'dart:io';
import 'dart:math' show max, min;
import 'package:flutter/material.dart';
import 'collection.dart';
import 'cutout.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
import 'note_page.dart';
import 'palette.dart';
import 'records.dart';
import 'store.dart';
import 'templates.dart';
import 'type.dart';

/// Two-column masonry. Every tile is one column wide; its height is a ratio of its width,
/// always one of [ratios] so the grid stays calm and no tile gets extremely tall.
/// Rigid tiles (album art, posters, covers) keep their ratio; others may stretch a little at the end.
const ratios = [1.0, 1.25, 1.5];

/// Returns (x, y, w, h) in pixels.
List<(double, double, double, double)> pack(List<(double, bool)> spans, double colW, double gap) {
  final y = [0.0, 0.0];
  final last = [-1, -1];
  final out = <(double, double, double, double)>[];
  for (var i = 0; i < spans.length; i++) {
    final c = y[0] <= y[1] ? 0 : 1, h = spans[i].$1 * colW;
    out.add((c * (colW + gap), y[c], colW, h));
    y[c] += h + gap;
    last[c] = i;
  }
  // even out the bottom: stretch the shorter column's last tile a little, if it is allowed to
  final s = y[0] < y[1] ? 0 : 1, d = (y[1 - s] - y[s]);
  if (d > 0 && last[s] >= 0 && !spans[last[s]].$2) {
    final (x, yy, w, h) = out[last[s]];
    if (d < h * .4) out[last[s]] = (x, yy, w, h + d);
  }
  return out;
}

double fitSize(String text, TextStyle Function(double) st, double w, double h, double maxS, double minS) {
  var s = maxS;
  while (s > minS) {
    final tp = TextPainter(text: TextSpan(text: text, style: st(s)), textDirection: TextDirection.ltr)..layout(maxWidth: w);
    if (tp.height <= h) break;
    s -= 1;
  }
  return s;
}

class Home extends StatefulWidget {
  const Home({super.key, required this.store, required this.onSeed, required this.onMode});
  final Store store;
  final VoidCallback onSeed, onMode;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> with SingleTickerProviderStateMixin {
  String q = '';
  bool searching = false, fabOpen = false, cutting = false;
  int tab = 0; // 0 tags, 1 notes, 2 collection
  late final AnimationController fab = AnimationController(vsync: this, duration: const Duration(milliseconds: 520));

  static const menu = [
    (Icons.notes, 'Note', Kind.text),
    (Icons.photo_library_outlined, 'Photos', Kind.photo),
    (Icons.format_quote, 'Quote', Kind.quote),
    (Icons.movie_outlined, 'Film', Kind.film),
    (Icons.music_note_outlined, 'Track', Kind.track),
    (Icons.menu_book_outlined, 'Book', Kind.book),
    (Icons.restaurant_menu, 'Recipe', Kind.recipe),
    (Icons.link, 'Link', Kind.link),
  ];

  @override
  void initState() {
    super.initState();
    backfill();
  }

  @override
  void dispose() {
    fab.dispose();
    super.dispose();
  }

  void setFab(bool open) {
    setState(() => fabOpen = open);
    open ? fab.forward() : fab.reverse();
  }

  /// Cuts the subject out of every photo / recipe image that has not been cut yet (one at a time).
  Future<void> backfill() async {
    if (cutting) return;
    cutting = true;
    for (final n in widget.store.notes.toList()) {
      for (final b in n.blocks) {
        if (!wantsCut(b)) continue;
        final before = b.data['cut'];
        await ensureCut(b, widget.store);
        if (mounted && b.data['cut'] != before) setState(() {});
      }
    }
    cutting = false;
  }

  Future<void> go(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    if (!mounted) return;
    setState(() {});
    backfill();
  }

  void edit([Note? n, String? tag, Kind? initial]) => go(Editor(store: widget.store, note: n, tag: tag, initial: initial));

  /// Only real titles count (a smart-layout note shows the headline it found); a note without one is shown by its text.
  String titleOf(Note n) {
    var t = '';
    for (final b in n.blocks) {
      if (b.kind == Kind.checklist) continue;
      final s = tplHead(b.kind, b.title, b.body);
      if (s.isNotEmpty) {
        t = s;
        break;
      }
    }
    return t.length > 44 ? '${t.substring(0, 44)}…' : t;
  }

  String snippet(Note n, String title) {
    for (final b in n.blocks) {
      final s = isRec(b.kind)
          ? recSub(b.kind, b.data)
          : b.kind == Kind.checklist
              ? b.items.take(3).join(' · ')
              : isTpl(b.kind)
                  ? tplSummary(b.kind, b.title, b.body)
                  : b.body;
      if (s.isNotEmpty && s != title) return s;
    }
    return '';
  }

  /// Tile shape: (height / width, rigid). Music is always a square, films and books a 2:3 portrait.
  (double, bool) spanFor(Note n) {
    final rec = n.blocks.where((b) => isRec(b.kind)).firstOrNull;
    if (rec != null) {
      return switch (rec.kind) {
        Kind.film || Kind.book => (1.5, true),
        Kind.track => (1.0, true),
        Kind.recipe => (cutPath(rec) != null ? 1.5 : 1.25, false),
        _ => (rec.path != null ? 1.25 : 1.0, false),
      };
    }
    final photos = [for (final b in n.blocks) if (b.kind == Kind.photo) b];
    final len = n.blocks.fold<int>(0, (s, b) => s + b.body.length);
    final tpl = n.blocks.where((b) => isTpl(b.kind)).firstOrNull;
    if (photos.length >= 2) return (1.25, false);
    if (photos.isNotEmpty) return (photos.first.aspect < .95 || cutPath(photos.first) != null ? 1.5 : photos.first.aspect < 1.1 ? 1.25 : 1.0, false);
    if (tpl != null) {
      return switch (tpl.kind) {
        Kind.stat => (1.0, false),
        Kind.quote => (len < 60 ? 1.0 : len < 140 ? 1.25 : 1.5, false),
        Kind.poster => (len < 90 ? 1.25 : 1.5, false),
        _ => (1.25, false),
      };
    }
    final items = n.blocks.where((b) => b.kind == Kind.checklist).firstOrNull?.items.length;
    if (items != null) return (items <= 3 ? 1.0 : items <= 7 ? 1.25 : 1.5, false);
    return (len <= 50 ? 1.0 : len <= 160 ? 1.25 : 1.5, false);
  }

  TextStyle caps(Color c, [double size = 11]) => TextStyle(fontSize: size, fontWeight: FontWeight.w700, letterSpacing: .6, color: c);

  Widget pill(String t, Color bg, Color fg) =>
      Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)), child: Text(t, style: caps(fg)));

  Widget header(ColorScheme cs) => Padding(
        padding: const EdgeInsets.fromLTRB(6, 6, 0, 14),
        child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          Expanded(
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(tab == 2 ? 'Collection' : tab == 1 ? 'Notes' : 'Tags', maxLines: 1, softWrap: false, style: sans(44, wght: 800, wdth: 110, color: cs.onSurface, ls: -1)),
            ),
          ),
          IconButton(icon: Icon(searching ? Icons.close : Icons.search, color: cs.onSurface), onPressed: () => setState(() {
                searching = !searching;
                if (!searching) q = '';
              })),
          IconButton(icon: Icon(Icons.brightness_6_outlined, color: cs.onSurface), onPressed: widget.onMode),
          IconButton(icon: Icon(Icons.palette_outlined, color: cs.onSurface), onPressed: widget.onSeed),
        ]),
      );

  /// A quote tile is the quote itself, sized to fit whole; the author (the title) is a small credit.
  Widget quoteTile(Block qb, double w, double h, ColorScheme cs) {
    final fg = cs.onPrimary, body = qb.body.trim();
    return ColoredBox(
      color: cs.primary,
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('“', style: serif(46, color: fg.withAlpha(150), h: .8)),
          Expanded(
            child: LayoutBuilder(builder: (_, c) {
              final s = fitSize(body, (f) => serif(f, italic: true, h: 1.2), c.maxWidth, c.maxHeight, 30, 12);
              return Align(alignment: Alignment.centerLeft, child: Text(body, style: serif(s, color: fg, italic: true, h: 1.2)));
            }),
          ),
          if (qb.title.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text('— ${qb.title}', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: fg.withAlpha(210)))),
        ]),
      ),
    );
  }

  /// Photo (or poster, album art, dish) as background. Film, music and book art stay clean with the
  /// title at the bottom. Now and then (about one note in four) a photo with a clear subject gets the title
  /// tucked behind its top edge: that is the only place the depth effect shows.
  Widget tile(Note n, double w, double h, ColorScheme cs, int i) {
    final roles = <(Color, Color)>[
      (cs.primaryContainer, cs.onPrimaryContainer),
      (cs.secondaryContainer, cs.onSecondaryContainer),
      (cs.tertiaryContainer, cs.onTertiaryContainer),
      (cs.surfaceContainerHighest, cs.onSurface),
    ];
    final rec = n.blocks.where((b) => isRec(b.kind)).firstOrNull;
    final cover = n.blocks.where((b) => (b.kind == Kind.photo || isRec(b.kind)) && b.path != null && File(b.path!).existsSync()).firstOrNull;
    final tp = n.blocks.where((b) => isTpl(b.kind)).firstOrNull;
    final qb = n.blocks.where((b) => b.kind == Kind.quote && b.body.trim().isNotEmpty).firstOrNull;
    if (qb != null && cover == null) return GestureDetector(onTap: () => go(NotePage(store: widget.store, note: n)), child: ClipRRect(borderRadius: BorderRadius.circular(28), child: quoteTile(qb, w, h, cs)));
    final art = cover != null && rec != null && (rec.kind == Kind.film || rec.kind == Kind.track || rec.kind == Kind.book);
    final (Color bg, Color fg) = cover != null ? (Colors.black, Colors.white) : tp != null ? tplColors(tp.kind, cs) : roles[i % 4];
    final title = titleOf(n), snip = snippet(n, title);
    final big = title.isEmpty && cover == null && snip.isNotEmpty, chip = rec != null && !art && w > 200;
    final fs = (w * .085).clamp(17.0, 28.0).toDouble();
    final al = cover != null && cover.kind == Kind.photo ? Alignment(cover.focal.center.dx * 2 - 1, cover.focal.center.dy * 2 - 1) : Alignment.center;

    // depth effect: needs a real cut-out, a title, and a spot where the title fits above the subject
    final cut = cover == null || art ? null : cutPath(cover);
    final cr = cut == null ? null : cutRectIn(cover!, Size(w, h), al);
    double? depthTop;
    if (cr != null && title.isNotEmpty && seedOf(n.id) % 4 == 0) {
      final tl = titleLay(title, w - 32, n.id, maxLines: 2, grade: -25);
      final t = cr.top - tl.height * .88; // the subject covers only the bottom of the last line
      if (t >= 10 && t + tl.height <= h * .6) depthTop = t;
    }
    final depth = depthTop != null;
    Widget img(String p, int cw) => Image.file(File(p), fit: BoxFit.cover, alignment: al, cacheWidth: cw);
    return GestureDetector(
      onTap: () => go(NotePage(store: widget.store, note: n)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: Stack(fit: StackFit.expand, children: [
          ColoredBox(color: bg),
          if (cover != null) ...[
            img(cover.path!, 800),
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: art
                    ? LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black.withAlpha(200)], stops: const [.5, 1])
                    : LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.black.withAlpha(150), Colors.transparent, Colors.transparent, Colors.black.withAlpha(170)], stops: const [0, .42, .6, 1]),
              ),
            ),
          ],
          if (art) ...[
            Positioned(
              top: 12,
              left: 12,
              child: Container(padding: const EdgeInsets.all(7), decoration: const BoxDecoration(color: Colors.black38, shape: BoxShape.circle), child: Icon(recMeta[rec!.kind]!.$2, size: 15, color: Colors.white)),
            ),
            Positioned(
              left: 14,
              right: 14,
              bottom: 12,
              child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (title.isNotEmpty) FitTitle(title, color: Colors.white, maxLines: 2, maxHeight: h * .26, seed: n.id),
                if (snip.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text(snip, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.white70))),
              ]),
            ),
          ] else if (title.isNotEmpty)
            Positioned(left: 16, top: depth ? depthTop : 14, right: 16, child: FitTitle(title, color: fg, maxLines: depth ? 2 : 3, maxHeight: h * .4, seed: n.id)),
          if (depth) img(cut!, 800), // the subject rides over the bottom of the title
          if (!art && big)
            Positioned(
              left: 16,
              top: 16,
              right: 16,
              bottom: 16,
              child: LayoutBuilder(builder: (_, c) {
                final s = fitSize(snip, (f) => serif(f, h: 1.2), c.maxWidth, c.maxHeight, fs + 4, 12);
                return Text(snip, style: serif(s, color: fg, h: 1.2));
              }),
            )
          else if (!art && snip.isNotEmpty)
            Positioned(
              right: 16,
              bottom: 14,
              left: chip ? w * .42 : w * .3,
              child: Text(snip, textAlign: TextAlign.right, maxLines: h > 150 ? 4 : 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, height: 1.3, color: fg.withAlpha(230))),
            ),
          if (chip)
            Positioned(
              left: 14,
              bottom: 14,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(color: Colors.black38, borderRadius: BorderRadius.circular(14)),
                child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(recMeta[rec!.kind]!.$2, size: 14, color: Colors.white), const SizedBox(width: 5), Text(recMeta[rec.kind]!.$1, style: const TextStyle(color: Colors.white, fontSize: 11.5, fontWeight: FontWeight.w600))]),
              ),
            ),
        ]),
      ),
    );
  }

  Widget bento(List<Note> notes, ColorScheme cs, double width) {
    const gap = 10.0;
    final colW = (width - gap) / 2, slots = pack([for (final n in notes) spanFor(n)], colW, gap);
    final total = slots.fold<double>(0, (m, s) => max(m, s.$2 + s.$4));
    return SizedBox(
      height: total,
      child: Stack(children: [
        for (var i = 0; i < notes.length; i++)
          Positioned(left: slots[i].$1, top: slots[i].$2, width: slots[i].$3, height: slots[i].$4, child: tile(notes[i], slots[i].$3, slots[i].$4, cs, i)),
      ]),
    );
  }

  /// A tag is an entry to its canvas.
  Widget tagTile(String tag, int i, ColorScheme g) {
    final paths = widget.store.photosFor(tag), n = widget.store.count(tag);
    return FutureBuilder<Color?>(
      initialData: peekSeed(paths),
      future: seedFromPhotos(paths),
      builder: (_, snap) {
        final cs = snap.data == null ? g : ColorScheme.fromSeed(seedColor: snap.data!, brightness: Theme.of(context).brightness);
        return GestureDetector(
          onTap: () => go(Board(store: widget.store, tag: tag, onSeed: widget.onSeed)),
          child: Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(color: cs.primaryContainer, borderRadius: BorderRadius.circular(i.isEven ? 36 : 22)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              pill('$n NOTE${n == 1 ? '' : 'S'}', cs.onPrimaryContainer, cs.primaryContainer),
              const Spacer(),
              Text(tag, maxLines: 2, overflow: TextOverflow.ellipsis, style: sans(26, wght: 700, h: 1.05, color: cs.onPrimaryContainer)),
              if (paths.isNotEmpty) ...[
                const SizedBox(height: 10),
                Row(children: [for (final p in paths.take(3)) Padding(padding: const EdgeInsets.only(right: 6), child: ClipOval(child: Image.file(File(p), width: 28, height: 28, fit: BoxFit.cover, cacheWidth: 90)))]),
              ],
            ]),
          ),
        );
      },
    );
  }

  // ---- floating toolbar and add menu ----

  Widget navItem(IconData icon, String label, bool on, ColorScheme cs, VoidCallback f) => GestureDetector(
        onTap: f,
        behavior: HitTestBehavior.opaque,
        child: AnimatedSize(
          duration: const Duration(milliseconds: 280),
          curve: Curves.easeOutBack,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            decoration: BoxDecoration(color: on ? cs.primaryContainer : Colors.transparent, borderRadius: BorderRadius.circular(26)),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(icon, size: 22, color: on ? cs.onPrimaryContainer : cs.onSurfaceVariant),
              if (on) Padding(padding: const EdgeInsets.only(left: 8), child: Text(label, maxLines: 1, softWrap: false, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: cs.onPrimaryContainer))),
            ]),
          ),
        ),
      );

  Widget bottomBar(ColorScheme cs) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 6, 14, 12),
          child: Row(children: [
            Expanded(
              child: Container(
                height: 64,
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: cs.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(32),
                  boxShadow: [BoxShadow(color: Colors.black.withAlpha(45), blurRadius: 16, offset: const Offset(0, 4))],
                ),
                child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                  navItem(Icons.sell_outlined, 'Tags', tab == 0, cs, () => setState(() => tab = 0)),
                  navItem(Icons.grid_view_rounded, 'Notes', tab == 1, cs, () => setState(() => tab = 1)),
                  navItem(Icons.auto_awesome_outlined, 'Collection', tab == 2, cs, () => setState(() => tab = 2)),
                ]),
              ),
            ),
            const SizedBox(width: 12),
            GestureDetector(
              onTap: () => setFab(!fabOpen),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 320),
                curve: Curves.easeOutBack,
                width: 64,
                height: 64,
                decoration: BoxDecoration(
                  color: fabOpen ? cs.tertiaryContainer : cs.primaryContainer,
                  borderRadius: BorderRadius.circular(fabOpen ? 32 : 22),
                  boxShadow: [BoxShadow(color: Colors.black.withAlpha(45), blurRadius: 16, offset: const Offset(0, 4))],
                ),
                child: AnimatedRotation(
                  turns: fabOpen ? .125 : 0,
                  duration: const Duration(milliseconds: 320),
                  curve: Curves.easeOutBack,
                  child: Icon(Icons.add, size: 32, color: fabOpen ? cs.onTertiaryContainer : cs.onPrimaryContainer),
                ),
              ),
            ),
          ]),
        ),
      );

  /// The add menu: equal-width items stacked right above the + button, each springing out of it in turn
  /// (the nearest first). It sits in the body, so it never covers the toolbar.
  Widget fabMenu(ColorScheme cs) => Positioned(
        right: 14,
        bottom: 6,
        child: AnimatedBuilder(
          animation: fab,
          builder: (_, __) => Column(crossAxisAlignment: CrossAxisAlignment.end, mainAxisSize: MainAxisSize.min, children: [
            for (var i = 0; i < menu.length; i++)
              () {
                final j = menu.length - 1 - i; // 0 = the item nearest the + button
                final t = Interval(min(.5, j * .06), min(1.0, j * .06 + .5), curve: Curves.easeOutBack).transform(fab.value);
                return IgnorePointer(
                  ignoring: !fabOpen,
                  child: Opacity(
                    opacity: t.clamp(0.0, 1.0),
                    child: Transform.translate(
                      offset: Offset(0, (1 - t) * 36),
                      child: Transform.scale(
                        scale: .6 + .4 * t,
                        alignment: Alignment.bottomRight,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: GestureDetector(
                            onTap: () {
                              setFab(false);
                              edit(null, null, menu[i].$3);
                            },
                            child: Container(
                              width: 172,
                              height: 52,
                              padding: const EdgeInsets.symmetric(horizontal: 18),
                              decoration: BoxDecoration(color: cs.secondaryContainer, borderRadius: BorderRadius.circular(26), boxShadow: [BoxShadow(color: Colors.black.withAlpha(40), blurRadius: 10, offset: const Offset(0, 3))]),
                              child: Row(children: [
                                Icon(menu[i].$1, size: 22, color: cs.onSecondaryContainer),
                                const SizedBox(width: 12),
                                Text(menu[i].$2, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: cs.onSecondaryContainer)),
                              ]),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              }(),
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final s = widget.store, cs = Theme.of(context).colorScheme, qq = q.toLowerCase();
    bool hit(Note n) => qq.isEmpty || n.tags.any((t) => t.toLowerCase().contains(qq)) || n.blocks.any((b) => '${b.title} ${b.body}'.toLowerCase().contains(qq));
    final all = s.notes.where(hit).toList().reversed.toList();
    final tags = s.tags.where((t) => all.any((n) => n.tags.contains(t))).toList()..sort();
    return Scaffold(
      backgroundColor: cs.surface,
      bottomNavigationBar: bottomBar(cs),
      body: Stack(children: [
        Positioned.fill(
          child: SafeArea(
            child: ListView(padding: const EdgeInsets.fromLTRB(14, 8, 14, 16), children: [
              header(cs),
              if (searching)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: TextField(
                    autofocus: true,
                    onChanged: (v) => setState(() => q = v),
                    decoration: InputDecoration(hintText: 'Search notes and tags', filled: true, prefixIcon: const Icon(Icons.search), border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none)),
                  ),
                ),
              if (tab == 2)
                CollectionView(store: widget.store)
              else if (tab == 0)
                GridView.count(
                  crossAxisCount: 2,
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  children: [for (var i = 0; i < tags.length; i++) tagTile(tags[i], i, cs)],
                )
              else if (all.isNotEmpty)
                LayoutBuilder(builder: (_, c) => bento(all, cs, c.maxWidth)),
              if (tab != 2 && (tab == 0 ? tags.isEmpty : all.isEmpty))
                Padding(padding: const EdgeInsets.all(24), child: Center(child: Text(tab == 0 ? 'Tag a note to create its canvas.' : 'Nothing here yet. Tap + to add a note.'))),
            ]),
          ),
        ),
        if (fabOpen) Positioned.fill(child: GestureDetector(onTap: () => setFab(false), child: ColoredBox(color: cs.scrim.withAlpha(110)))),
        fabMenu(cs),
      ]),
    );
  }
}
