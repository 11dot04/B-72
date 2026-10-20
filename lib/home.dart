import 'dart:io';
import 'package:flutter/material.dart';
import 'collection.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
import 'note_page.dart';
import 'palette.dart';
import 'store.dart';

const _r = 26.0, _g = 8.0, _d = 18.0; // corner radius, seam gap, notch depth

/// Panels fused vertically. At each seam the edges notch inward, as in the BRIK concept.
class Fused extends StatelessWidget {
  const Fused({super.key, required this.color, required this.children});
  final Color color;
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Column(children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: _d),
              child: SizedBox(height: _g, width: double.infinity, child: ColoredBox(color: color)),
            ),
          DecoratedBox(
            decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(_r)),
            child: SizedBox(width: double.infinity, child: children[i]),
          ),
        ],
      ]);
}

class Home extends StatefulWidget {
  const Home({super.key, required this.store, required this.onSeed});
  final Store store;
  final VoidCallback onSeed;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  String q = '';
  bool searching = false;
  int tab = 0; // 0 tags, 1 notes, 2 collection
  bool get byTag => tab == 0;
  String? open;

  Future<void> go(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    setState(() {});
  }

  void edit([Note? n, String? tag]) => go(Editor(store: widget.store, note: n, tag: tag));

  String headline(Note n) => n.blocks.map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () => n.blocks.map((b) => b.body).firstWhere((s) => s.isNotEmpty, orElse: () => 'Photo'));
  List<String> photosOf(Note n) => [for (final b in n.blocks) if (b.kind == Kind.photo && b.path != null) b.path!];

  TextStyle caps(Color c, [double size = 11]) => TextStyle(fontSize: size, fontWeight: FontWeight.w700, letterSpacing: .6, color: c);

  Widget pill(String t, Color bg, Color fg) =>
      Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)), child: Text(t, style: caps(fg)));

  Widget header(ColorScheme cs, int n) => Fused(color: cs.onPrimaryContainer, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 16, 14),
          child: Row(children: [
            Text('Notes', style: TextStyle(fontFamily: 'serif', fontSize: 28, color: cs.primaryContainer)),
            const Spacer(),
            IconButton(icon: Icon(Icons.palette_outlined, color: cs.primaryContainer), onPressed: widget.onSeed),
            pill('$n NOTES', cs.primaryContainer, cs.onPrimaryContainer),
          ]),
        ),
      ]);

  /// Light outlined row with a chevron (collapsed accordion).
  Widget row(String name, String trail, ColorScheme cs, VoidCallback onTap) => GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
          decoration: BoxDecoration(color: cs.surface, borderRadius: BorderRadius.circular(_r), border: Border.all(color: cs.onPrimaryContainer.withAlpha(120))),
          child: Row(children: [
            Container(width: 10, height: 10, decoration: BoxDecoration(color: cs.primary, shape: BoxShape.circle)),
            const SizedBox(width: 12),
            Expanded(child: Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 17, color: cs.onSurface))),
            Text(trail, style: caps(cs.onSurface.withAlpha(150))),
            const SizedBox(width: 6),
            Icon(Icons.chevron_right, size: 20, color: cs.onSurface),
          ]),
        ),
      );

  Widget panel(String label, Widget child, Color light) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 16, 16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: caps(light.withAlpha(150), 10)),
          const SizedBox(height: 6),
          child,
        ]),
      );

  Widget expandedNote(Note n, ColorScheme cs) {
    final light = cs.primaryContainer, txt = TextStyle(fontSize: 14, height: 1.35, color: light.withAlpha(220));
    final photos = photosOf(n);
    Widget check(String s) => Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Row(children: [
            Icon(s.startsWith('x ') ? Icons.radio_button_checked : Icons.radio_button_unchecked, size: 16, color: light),
            const SizedBox(width: 8),
            Expanded(child: Text(s.startsWith('x ') ? s.substring(2) : s, maxLines: 1, overflow: TextOverflow.ellipsis, style: txt.copyWith(decoration: s.startsWith('x ') ? TextDecoration.lineThrough : null))),
          ]),
        );
    return Fused(color: cs.onPrimaryContainer, children: [
      GestureDetector(
        onTap: () => setState(() => open = null),
        behavior: HitTestBehavior.opaque,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 16, 18),
          child: Row(children: [
            Expanded(child: Text(headline(n), maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'serif', fontSize: 22, height: 1.1, color: light))),
            Icon(Icons.expand_more, color: light),
          ]),
        ),
      ),
      for (final b in n.blocks)
        if (b.kind == Kind.text && b.body.isNotEmpty)
          panel('TEXT', Text(b.body, maxLines: 5, overflow: TextOverflow.ellipsis, style: txt), light)
        else if (b.kind == Kind.checklist)
          panel('CHECKLIST · ${b.items.where((s) => s.startsWith('x ')).length}/${b.items.length}', Column(children: [for (final s in b.items.take(6)) check(s)]), light),
      if (photos.isNotEmpty)
        panel(
          'PHOTOS · ${photos.length}',
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final p in photos.take(8)) ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(p), width: 56, height: 56, fit: BoxFit.cover, cacheWidth: 160)),
          ]),
          light,
        ),
      Padding(
        padding: const EdgeInsets.all(14),
        child: Row(children: [
          Expanded(
            child: FilledButton(
              onPressed: () => go(NotePage(store: widget.store, note: n)),
              style: FilledButton.styleFrom(backgroundColor: light, foregroundColor: cs.onPrimaryContainer, padding: const EdgeInsets.symmetric(vertical: 16), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
              child: Text('OPEN', style: caps(cs.onPrimaryContainer, 12)),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: OutlinedButton(
              onPressed: () => edit(n),
              style: OutlinedButton.styleFrom(foregroundColor: light, side: BorderSide(color: light.withAlpha(150)), padding: const EdgeInsets.symmetric(vertical: 16), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
              child: Text('EDIT', style: caps(light, 12)),
            ),
          ),
        ]),
      ),
    ]);
  }

  /// Each multi-part note is an accordion, themed from its own photos.
  Widget noteRow(Note n, ColorScheme g) {
    final paths = photosOf(n);
    return FutureBuilder<Color?>(
      initialData: peekSeed(paths),
      future: seedFromPhotos(paths),
      builder: (_, snap) {
        final cs = snap.data == null ? g : ColorScheme.fromSeed(seedColor: snap.data!);
        return AnimatedSize(
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
          alignment: Alignment.topCenter,
          child: open == n.id ? expandedNote(n, cs) : row(headline(n), n.tags.isEmpty ? 'UNTAGGED' : n.tags.first.toUpperCase(), cs, () => setState(() => open = n.id)),
        );
      },
    );
  }

  /// A tag is an entry to its canvas.
  Widget tagTile(String tag, int i, ColorScheme g) {
    final paths = widget.store.photosFor(tag), n = widget.store.count(tag);
    return FutureBuilder<Color?>(
      initialData: peekSeed(paths),
      future: seedFromPhotos(paths),
      builder: (_, snap) {
        final cs = snap.data == null ? g : ColorScheme.fromSeed(seedColor: snap.data!);
        return GestureDetector(
          onTap: () => go(Board(store: widget.store, tag: tag, onSeed: widget.onSeed)),
          child: Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(color: cs.primaryContainer, borderRadius: BorderRadius.circular(i.isEven ? 36 : 22)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              pill('$n NOTE${n == 1 ? '' : 'S'}', cs.onPrimaryContainer, cs.primaryContainer),
              const Spacer(),
              Text(tag, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'serif', fontSize: 26, height: 1.05, color: cs.onPrimaryContainer)),
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

  Widget circle(IconData i, ColorScheme cs, VoidCallback f) => GestureDetector(
        onTap: f,
        child: Container(width: 52, height: 52, decoration: BoxDecoration(color: cs.onPrimaryContainer, shape: BoxShape.circle), child: Icon(i, color: cs.primaryContainer)),
      );

  Widget seg(String t, bool on, ColorScheme cs, VoidCallback f) => GestureDetector(
        onTap: f,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 15),
          decoration: BoxDecoration(color: on ? cs.primaryContainer : Colors.transparent, borderRadius: BorderRadius.circular(30)),
          child: Text(t, style: caps(on ? cs.onPrimaryContainer : cs.primaryContainer, 11)),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final s = widget.store, cs = Theme.of(context).colorScheme, qq = q.toLowerCase();
    bool hit(Note n) => qq.isEmpty || n.tags.any((t) => t.toLowerCase().contains(qq)) || n.blocks.any((b) => '${b.title} ${b.body}'.toLowerCase().contains(qq));
    final all = s.notes.where(hit).toList().reversed.toList();
    final tags = s.tags.where((t) => all.any((n) => n.tags.contains(t))).toList()..sort();
    Widget gap(Widget w) => Padding(padding: const EdgeInsets.only(bottom: 10), child: w);
    return Scaffold(
      backgroundColor: cs.surface,
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 4, 14, 12),
          child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            circle(Icons.add, cs, () => edit()),
            Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(color: cs.onPrimaryContainer, borderRadius: BorderRadius.circular(40)),
              child: Row(children: [seg('TAGS', tab == 0, cs, () => setState(() => tab = 0)), seg('NOTES', tab == 1, cs, () => setState(() => tab = 1)), seg('COLLECTION', tab == 2, cs, () => setState(() => tab = 2))]),
            ),
            circle(searching ? Icons.close : Icons.search, cs, () => setState(() {
                  searching = !searching;
                  if (!searching) q = '';
                })),
          ]),
        ),
      ),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(14, 8, 14, 16), children: [
          gap(header(cs, s.notes.length)),
          if (searching)
            gap(TextField(
              autofocus: true,
              onChanged: (v) => setState(() => q = v),
              decoration: InputDecoration(hintText: 'Search notes and tags', filled: true, prefixIcon: const Icon(Icons.search), border: OutlineInputBorder(borderRadius: BorderRadius.circular(_r), borderSide: BorderSide.none)),
            )),
          if (tab == 2)
            CollectionView(store: widget.store)
          else if (byTag)
            GridView.count(
              crossAxisCount: 2,
              mainAxisSpacing: 10,
              crossAxisSpacing: 10,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              children: [for (var i = 0; i < tags.length; i++) tagTile(tags[i], i, cs)],
            )
          else
            for (final n in all) gap(noteRow(n, cs)),
          if (tab != 2 && (byTag ? tags.isEmpty : all.isEmpty))
            Padding(padding: const EdgeInsets.all(24), child: Center(child: Text(byTag ? 'Tag a note to create its canvas.' : 'Nothing here yet. Tap + to add a note.'))),
        ]),
      ),
    );
  }
}
