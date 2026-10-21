import 'dart:io';
import 'dart:math' show max;
import 'package:flutter/material.dart';
import 'collection.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
import 'note_page.dart';
import 'palette.dart';
import 'records.dart';
import 'store.dart';
import 'templates.dart';
import 'type.dart';

/// Packs spans (cols, rows) into a 4-column grid, first fit, then stretches tiles into leftover holes.
List<(int, int, int, int)> pack(List<(int, int)> spans) {
  final grid = <List<bool>>[];
  bool free(int x, int y, int w, int h) {
    for (var r = y; r < y + h; r++) {
      if (r >= grid.length) continue;
      for (var c = x; c < x + w; c++) {
        if (grid[r][c]) return false;
      }
    }
    return true;
  }

  void mark(int x, int y, int w, int h) {
    while (grid.length < y + h) {
      grid.add(List.filled(4, false));
    }
    for (var r = y; r < y + h; r++) {
      for (var c = x; c < x + w; c++) {
        grid[r][c] = true;
      }
    }
  }

  final out = <(int, int, int, int)>[];
  for (final s in spans) {
    var placed = false;
    for (var y = 0; !placed; y++) {
      for (var x = 0; x + s.$1 <= 4 && !placed; x++) {
        if (free(x, y, s.$1, s.$2)) {
          mark(x, y, s.$1, s.$2);
          out.add((x, y, s.$1, s.$2));
          placed = true;
        }
      }
    }
  }
  for (var i = 0; i < out.length; i++) {
    var (x, y, w, h) = out[i];
    while (x + w < 4 && [for (var r = y; r < y + h; r++) grid[r][x + w]].every((v) => !v)) {
      mark(x + w, y, 1, h);
      w++;
    }
    while (y + h < grid.length && [for (var c = x; c < x + w; c++) grid[y + h][c]].every((v) => !v)) {
      mark(x, y + h, w, 1);
      h++;
    }
    out[i] = (x, y, w, h);
  }
  return out;
}

class Home extends StatefulWidget {
  const Home({super.key, required this.store, required this.onSeed, required this.onMode});
  final Store store;
  final VoidCallback onSeed, onMode;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  String q = '';
  bool searching = false, fabOpen = false;
  int tab = 0; // 0 tags, 1 notes, 2 collection

  static const menu = [
    (Icons.notes, 'Note', Kind.text),
    (Icons.photo_library_outlined, 'Photos', Kind.photo),
    (Icons.format_quote, 'Quote', Kind.quote),
    (Icons.movie_outlined, 'Film', Kind.film),
    (Icons.music_note_outlined, 'Track', Kind.track),
    (Icons.restaurant_menu, 'Recipe', Kind.recipe),
    (Icons.link, 'Link', Kind.link),
  ];

  Future<void> go(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    setState(() {});
  }

  void edit([Note? n, String? tag, Kind? initial]) => go(Editor(store: widget.store, note: n, tag: tag, initial: initial));

  /// Only real titles count; a note without one is shown by its text.
  String titleOf(Note n) {
    final t = n.blocks.where((b) => b.kind != Kind.checklist).map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () => '');
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

  /// How big a note's card is, in grid cells (cols, rows), from what it contains.
  (int, int) spanFor(Note n) {
    final rec = n.blocks.where((b) => isRec(b.kind)).firstOrNull;
    if (rec != null) {
      return switch (rec.kind) {
        Kind.film => (2, 3),
        Kind.track => (2, 2),
        Kind.recipe => (3, 2),
        _ => rec.path != null ? (3, 2) : (2, 2),
      };
    }
    final photos = [for (final b in n.blocks) if (b.kind == Kind.photo) b];
    final len = n.blocks.fold<int>(0, (s, b) => s + b.body.length);
    final tpl = n.blocks.where((b) => isTpl(b.kind)).firstOrNull;
    if (photos.length >= 3) return (3, 3);
    if (photos.isNotEmpty) return len > 140 ? (3, 3) : (photos.first.aspect < .95 ? (3, 2) : (2, 3));
    if (tpl != null) return switch (tpl.kind) { Kind.stat => (2, 2), Kind.quote => len < 90 ? (2, 2) : (3, 2), _ => (2, 3) };
    final items = n.blocks.where((b) => b.kind == Kind.checklist).firstOrNull?.items.length;
    if (items != null) return items <= 3 ? (2, 2) : (2, 3);
    return len <= 50 ? (2, 2) : len <= 160 ? (3, 2) : (3, 3);
  }

  TextStyle caps(Color c, [double size = 11]) => TextStyle(fontSize: size, fontWeight: FontWeight.w700, letterSpacing: .6, color: c);

  Widget pill(String t, Color bg, Color fg) =>
      Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)), child: Text(t, style: caps(fg)));

  Widget header(ColorScheme cs) => Padding(
        padding: const EdgeInsets.fromLTRB(6, 6, 0, 14),
        child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(child: Text(tab == 2 ? 'Collection' : tab == 1 ? 'Notes' : 'Tags', style: sans(44, wght: 800, wdth: 110, color: cs.onSurface, ls: -1))),
          IconButton(icon: Icon(searching ? Icons.close : Icons.search, color: cs.onSurface), onPressed: () => setState(() {
                searching = !searching;
                if (!searching) q = '';
              })),
          IconButton(icon: Icon(Icons.brightness_6_outlined, color: cs.onSurface), onPressed: widget.onMode),
          IconButton(icon: Icon(Icons.palette_outlined, color: cs.onSurface), onPressed: widget.onSeed),
        ]),
      );

  /// Cards: photo (or poster, album art, dish) as background, title top-left, snippet bottom-right.
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
    final (Color bg, Color fg) = cover != null ? (Colors.black, Colors.white) : tp != null ? tplColors(tp.kind, cs) : roles[i % 4];
    final title = titleOf(n), snip = snippet(n, title);
    final big = title.isEmpty && cover == null && snip.isNotEmpty, chip = rec != null && w > 200;
    final fs = (w * .085).clamp(17.0, 28.0).toDouble();
    return GestureDetector(
      onTap: () => go(NotePage(store: widget.store, note: n)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: Stack(fit: StackFit.expand, children: [
          ColoredBox(color: bg),
          if (cover != null) ...[
            Image.file(File(cover.path!), fit: BoxFit.cover, alignment: cover.kind == Kind.photo ? Alignment(cover.focal.center.dx * 2 - 1, cover.focal.center.dy * 2 - 1) : Alignment.center, cacheWidth: 800),
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.black.withAlpha(150), Colors.transparent, Colors.transparent, Colors.black.withAlpha(170)],
                  stops: const [0, .42, .6, 1],
                ),
              ),
            ),
          ],
          if (title.isNotEmpty) Positioned(left: 16, top: 14, right: 16, child: FitTitle(title, color: fg, maxHeight: h * .46)),
          if (big)
            Positioned(
              left: 16,
              top: 16,
              right: 16,
              bottom: 16,
              child: Text(snip, maxLines: ((h - 32) / (fs * 1.25)).floor().clamp(2, 14), overflow: TextOverflow.ellipsis, style: serif(fs, color: fg, h: 1.2)),
            )
          else if (snip.isNotEmpty)
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
                child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(recMeta[rec.kind]!.$2, size: 14, color: Colors.white), const SizedBox(width: 5), Text(recMeta[rec.kind]!.$1, style: const TextStyle(color: Colors.white, fontSize: 11.5, fontWeight: FontWeight.w600))]),
              ),
            ),
        ]),
      ),
    );
  }

  Widget bento(List<Note> notes, ColorScheme cs, double width) {
    const gap = 8.0;
    final cell = (width - 3 * gap) / 4, slots = pack([for (final n in notes) spanFor(n)]);
    final rows = slots.fold<int>(0, (m, s) => max(m, s.$2 + s.$4));
    return SizedBox(
      height: rows * (cell + gap) - gap,
      child: Stack(children: [
        for (var i = 0; i < notes.length; i++)
          Positioned(
            left: slots[i].$1 * (cell + gap),
            top: slots[i].$2 * (cell + gap),
            width: slots[i].$3 * cell + (slots[i].$3 - 1) * gap,
            height: slots[i].$4 * cell + (slots[i].$4 - 1) * gap,
            child: tile(notes[i], slots[i].$3 * cell + (slots[i].$3 - 1) * gap, slots[i].$4 * cell + (slots[i].$4 - 1) * gap, cs, i),
          ),
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

  // ---- floating toolbar and FAB menu ----

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
              if (on) Padding(padding: const EdgeInsets.only(left: 8), child: Text(label, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: cs.onPrimaryContainer))),
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
              onTap: () => setState(() => fabOpen = !fabOpen),
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

  Widget fabMenu(ColorScheme cs) => Positioned(
        right: 14,
        bottom: 8,
        child: Column(crossAxisAlignment: CrossAxisAlignment.end, mainAxisSize: MainAxisSize.min, children: [
          for (var i = 0; i < menu.length; i++)
            IgnorePointer(
              ignoring: !fabOpen,
              child: TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: fabOpen ? 1 : 0),
                duration: Duration(milliseconds: 170 + (i + 1) * 30),
                curve: Curves.easeOutBack,
                builder: (_, t, child) => Opacity(opacity: t.clamp(0.0, 1.0), child: Transform.scale(scale: .55 + .45 * t, alignment: Alignment.bottomRight, child: child)),
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: GestureDetector(
                    onTap: () {
                      setState(() => fabOpen = false);
                      edit(null, null, menu[i].$3);
                    },
                    child: Container(
                      padding: const EdgeInsets.fromLTRB(16, 13, 20, 13),
                      decoration: BoxDecoration(color: cs.secondaryContainer, borderRadius: BorderRadius.circular(30), boxShadow: [BoxShadow(color: Colors.black.withAlpha(35), blurRadius: 10, offset: const Offset(0, 3))]),
                      child: Row(mainAxisSize: MainAxisSize.min, children: [
                        Icon(menu[i].$1, size: 22, color: cs.onSecondaryContainer),
                        const SizedBox(width: 10),
                        Text(menu[i].$2, style: TextStyle(fontWeight: FontWeight.w700, color: cs.onSecondaryContainer)),
                      ]),
                    ),
                  ),
                ),
              ),
            ),
          const SizedBox(height: 72),
        ]),
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
        if (fabOpen) Positioned.fill(child: GestureDetector(onTap: () => setState(() => fabOpen = false), child: ColoredBox(color: cs.scrim.withAlpha(110)))),
        fabMenu(cs),
      ]),
    );
  }
}
