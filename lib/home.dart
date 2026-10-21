import 'dart:io';
import 'dart:math' show max;
import 'package:flutter/material.dart';
import 'collection.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
import 'note_page.dart';
import 'palette.dart';
import 'records.dart';
import 'sticker.dart';
import 'store.dart';
import 'templates.dart';
import 'type.dart';

/// Two columns; each card goes under the shorter one. Full-width cards span both.
List<Rect> masonry(List<(bool, double)> items, double width) {
  const gap = 8.0;
  final cw = (width - gap) / 2, out = <Rect>[];
  var l = 0.0, r = 0.0;
  for (final (full, h) in items) {
    if (full) {
      final y = max(l, r);
      out.add(Rect.fromLTWH(0, y, width, h));
      l = r = y + h + gap;
    } else if (l <= r) {
      out.add(Rect.fromLTWH(0, l, cw, h));
      l += h + gap;
    } else {
      out.add(Rect.fromLTWH(cw + gap, r, cw, h));
      r += h + gap;
    }
  }
  return out;
}

/// Press feedback and a springy entrance for a card.
class _Pop extends StatefulWidget {
  const _Pop({required this.index, required this.onTap, required this.child});
  final int index;
  final VoidCallback onTap;
  final Widget child;
  @override
  State<_Pop> createState() => _PopState();
}

class _PopState extends State<_Pop> {
  bool down = false;
  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: 1),
        duration: Duration(milliseconds: 380 + widget.index.clamp(0, 8) * 50),
        curve: Curves.easeOutCubic,
        builder: (_, t, child) => Opacity(opacity: t, child: Transform.translate(offset: Offset(0, 28 * (1 - t)), child: child)),
        child: GestureDetector(
          onTap: widget.onTap,
          onTapDown: (_) => setState(() => down = true),
          onTapUp: (_) => setState(() => down = false),
          onTapCancel: () => setState(() => down = false),
          child: AnimatedScale(scale: down ? .96 : 1, duration: const Duration(milliseconds: 140), curve: Curves.easeOut, child: widget.child),
        ),
      );
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
  bool searching = false, fabOpen = false, _filling = false;
  int tab = 0; // 0 tags, 1 notes, 2 collection

  @override
  void initState() {
    super.initState();
    backfill();
  }

  /// Gives photos that were added earlier a subject cut-out too (once, in the background).
  Future<void> backfill() async {
    if (_filling) return;
    _filling = true;
    for (final n in [...widget.store.notes]) {
      for (final b in n.blocks) {
        if ((b.kind == Kind.photo || b.kind == Kind.recipe) && b.path != null && b.data['cutTried'] != true && File(b.path!).existsSync()) {
          final c = await cutoutFrame(b.path!, widget.store.dir);
          b.data['cutTried'] = true;
          if (c != null) b.data['cut'] = c;
          widget.store.save();
          if (mounted) setState(() {});
        }
      }
    }
    _filling = false;
  }

  static const menu = [
    (Icons.notes, 'Note', Kind.text),
    (Icons.photo_library_outlined, 'Photos', Kind.photo),
    (Icons.format_quote, 'Quote', Kind.quote),
    (Icons.movie_outlined, 'Film', Kind.film),
    (Icons.menu_book_outlined, 'Book', Kind.book),
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

  Block? recOf(Note n) => n.blocks.where((b) => isRec(b.kind)).firstOrNull;
  int photoCount(Note n) => n.blocks.where((b) => b.kind == Kind.photo).length;

  /// A few kinds of card take the full width.
  bool isFull(Note n) {
    final rec = recOf(n);
    if (rec != null) return (rec.kind == Kind.recipe || rec.kind == Kind.link) && rec.path != null;
    if (photoCount(n) >= 3) return true;
    final tpl = n.blocks.where((b) => isTpl(b.kind)).firstOrNull;
    if (tpl != null) return tpl.kind == Kind.quote && tpl.body.length > 90;
    return photoCount(n) == 0 && n.blocks.every((b) => b.kind != Kind.checklist) && n.blocks.fold<int>(0, (s, b) => s + b.body.length) > 260;
  }

  /// Fixed shapes where the content has one (album art is square, posters are portrait).
  double heightFor(Note n, double w, double width, bool full) {
    final rec = recOf(n);
    if (rec != null) {
      return switch (rec.kind) {
        Kind.track => w,
        Kind.film || Kind.book => w * 1.5,
        Kind.recipe => full ? width * .62 : w * 1.1,
        _ => full ? width * .55 : w * .9,
      };
    }
    final photos = [for (final b in n.blocks) if (b.kind == Kind.photo) b];
    if (photos.length >= 3) return width * .75;
    if (photos.isNotEmpty) return w * photos.first.aspect.clamp(.8, 1.35);
    final tpl = n.blocks.where((b) => isTpl(b.kind)).firstOrNull;
    if (tpl != null) return switch (tpl.kind) { Kind.stat => w * .85, Kind.quote => full ? width * .5 : w, Kind.info => w * 1.2, _ => w * 1.3 };
    final items = n.blocks.where((b) => b.kind == Kind.checklist).firstOrNull?.items.length;
    if (items != null) return (110 + items * 26.0).clamp(w * .8, w * 1.5).toDouble();
    final text = n.blocks.map((b) => b.body).firstWhere((s) => s.isNotEmpty, orElse: () => '');
    if (titleOf(n).isNotEmpty) return w;
    final fs = (w * .085).clamp(17.0, 28.0).toDouble();
    final tp = TextPainter(text: TextSpan(text: text, style: serif(fs, h: 1.2)), textDirection: TextDirection.ltr)..layout(maxWidth: w - 32);
    return (tp.height + 44).clamp(w * .72, width * 1.2).toDouble();
  }

  int variantFor(Note n) {
    final rec = recOf(n);
    return rec == null ? vOf(n.id) : switch (rec.kind) { Kind.track => 2, Kind.film => 3, Kind.book => 4, Kind.recipe => 1, _ => 0 };
  }

  TextStyle caps(Color c, [double size = 11]) => TextStyle(fontSize: size, fontWeight: FontWeight.w700, letterSpacing: .6, color: c);

  Widget pill(String t, Color bg, Color fg) =>
      Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)), child: Text(t, style: caps(fg)));

  Widget header(ColorScheme cs) => Padding(
        padding: const EdgeInsets.fromLTRB(6, 6, 0, 14),
        child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Expanded(child: FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerLeft, child: Text(tab == 2 ? 'Collection' : tab == 1 ? 'Notes' : 'Tags', maxLines: 1, style: sans(44, wght: 800, wdth: 110, color: cs.onSurface, ls: -1)))),
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
    final cutPath = cover == null ? '' : '${cover.data['cut'] ?? ''}';
    final behind = title.isNotEmpty && cutPath.isNotEmpty && File(cutPath).existsSync();
    final al = cover != null && cover.kind == Kind.photo ? Alignment(cover.focal.center.dx * 2 - 1, cover.focal.center.dy * 2 - 1) : Alignment.center;
    return _Pop(
      index: i,
      onTap: () => go(NotePage(store: widget.store, note: n)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: Stack(fit: StackFit.expand, children: [
          ColoredBox(color: bg),
          if (cover != null) ...[
            Image.file(File(cover.path!), fit: BoxFit.cover, alignment: al, cacheWidth: 800),
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.black.withAlpha(behind ? 90 : 150), Colors.transparent, Colors.transparent, Colors.black.withAlpha(170)],
                  stops: const [0, .42, .6, 1],
                ),
              ),
            ),
          ],
          if (title.isNotEmpty) Positioned(left: 16, top: 14, right: 16, child: FitTitle(title, color: fg, maxHeight: h * .5, variant: variantFor(n))),
          // the subject goes back on top, so the title sits behind it
          if (behind) Image.file(File(cutPath), fit: BoxFit.cover, alignment: al, cacheWidth: 800),
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

  Widget grid(List<Note> notes, ColorScheme cs, double width) {
    const gap = 8.0;
    final cw = (width - gap) / 2, items = <(bool, double)>[];
    for (final n in notes) {
      final f = isFull(n);
      items.add((f, heightFor(n, f ? width : cw, width, f)));
    }
    final rects = masonry(items, width);
    final total = rects.fold<double>(0, (m, r) => max(m, r.bottom));
    return SizedBox(
      height: total,
      child: Stack(children: [
        for (var i = 0; i < notes.length; i++)
          Positioned.fromRect(key: ValueKey(notes[i].id), rect: rects[i], child: tile(notes[i], rects[i].width, rects[i].height, cs, i)),
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
                LayoutBuilder(builder: (_, c) => grid(all, cs, c.maxWidth)),
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
