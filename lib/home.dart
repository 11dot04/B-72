import 'dart:io';
import 'package:flutter/material.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
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
  bool searching = false, byTag = true;
  String? open;

  Future<void> go(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    setState(() {});
  }

  void edit([Note? n, String? tag]) => go(Editor(store: widget.store, note: n, tag: tag));

  String headline(Note n) => n.blocks.map((b) => b.title).firstWhere((s) => s.isNotEmpty, orElse: () => n.blocks.map((b) => b.body).firstWhere((s) => s.isNotEmpty, orElse: () => 'Photo'));
  String snippet(Note n) => n.blocks.any((b) => b.title.isNotEmpty) ? n.blocks.map((b) => b.body).firstWhere((s) => s.isNotEmpty, orElse: () => '') : '';

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

  /// Light outlined row with a chevron (collapsed state).
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

  Widget notePanel(Note n, ColorScheme cs) {
    final light = cs.primaryContainer, sn = snippet(n);
    final photos = [for (final b in n.blocks) if (b.kind == Kind.photo && b.path != null) b.path!];
    final kinds = n.blocks.map((b) => b.kind == Kind.bare ? 'TEXT' : b.kind.name.toUpperCase()).toSet().join(' + ');
    return GestureDetector(
      onTap: () => edit(n),
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 16, 16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(kinds, style: caps(light.withAlpha(150), 10)),
          const SizedBox(height: 6),
          Text(headline(n), maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: 'serif', fontSize: 20, height: 1.15, color: light)),
          if (sn.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 4), child: Text(sn, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 13, color: light.withAlpha(190)))),
          if (photos.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Row(children: [
                for (final p in photos.take(4))
                  Padding(padding: const EdgeInsets.only(right: 8), child: ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(p), width: 56, height: 56, fit: BoxFit.cover, cacheWidth: 160))),
              ]),
            ),
        ]),
      ),
    );
  }

  Widget expanded(String name, String? tag, List<Note> notes, ColorScheme cs) {
    final light = cs.primaryContainer;
    return Fused(color: cs.onPrimaryContainer, children: [
      GestureDetector(
        onTap: () => setState(() => open = null),
        behavior: HitTestBehavior.opaque,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 16, 18),
          child: Row(children: [
            Expanded(child: Text(name, style: TextStyle(fontFamily: 'serif', fontSize: 22, color: light))),
            Icon(Icons.expand_more, color: light),
          ]),
        ),
      ),
      for (final n in notes) notePanel(n, cs),
      Padding(
        padding: const EdgeInsets.all(14),
        child: Row(children: [
          Expanded(
            child: FilledButton(
              onPressed: tag == null ? null : () => go(Board(store: widget.store, tag: tag, onSeed: widget.onSeed)),
              style: FilledButton.styleFrom(backgroundColor: light, foregroundColor: cs.onPrimaryContainer, padding: const EdgeInsets.symmetric(vertical: 16), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
              child: Text('OPEN CANVAS', style: caps(cs.onPrimaryContainer, 12)),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: OutlinedButton(
              onPressed: () => edit(null, tag),
              style: OutlinedButton.styleFrom(foregroundColor: light, side: BorderSide(color: light.withAlpha(150)), padding: const EdgeInsets.symmetric(vertical: 16), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
              child: Text('NEW NOTE', style: caps(light, 12)),
            ),
          ),
        ]),
      ),
    ]);
  }

  /// A tag accordion row, themed from the colours of that tag's own photos.
  Widget tagRow(String? tag, List<Note> notes, ColorScheme g) {
    final paths = tag == null ? <String>[] : widget.store.photosFor(tag), name = tag ?? 'Untagged';
    return FutureBuilder<Color?>(
      initialData: peekSeed(paths),
      future: seedFromPhotos(paths),
      builder: (_, snap) {
        final cs = snap.data == null ? g : ColorScheme.fromSeed(seedColor: snap.data!);
        return AnimatedSize(
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
          alignment: Alignment.topCenter,
          child: open == name ? expanded(name, tag, notes, cs) : row(name, '${notes.length}', cs, () => setState(() => open = name)),
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
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 15),
          decoration: BoxDecoration(color: on ? cs.primaryContainer : Colors.transparent, borderRadius: BorderRadius.circular(30)),
          child: Text(t, style: caps(on ? cs.onPrimaryContainer : cs.primaryContainer, 12)),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final s = widget.store, cs = Theme.of(context).colorScheme, qq = q.toLowerCase();
    bool hit(Note n) => qq.isEmpty || n.tags.any((t) => t.toLowerCase().contains(qq)) || n.blocks.any((b) => '${b.title} ${b.body}'.toLowerCase().contains(qq));
    final all = s.notes.where(hit).toList().reversed.toList();
    final tags = s.tags.where((t) => all.any((n) => n.tags.contains(t))).toList()..sort();
    final loose = all.where((n) => n.tags.isEmpty).toList();
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
              child: Row(children: [seg('TAGS', byTag, cs, () => setState(() => byTag = true)), seg('NOTES', !byTag, cs, () => setState(() => byTag = false))]),
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
          if (byTag) ...[
            for (final t in tags) gap(tagRow(t, all.where((n) => n.tags.contains(t)).toList(), cs)),
            if (loose.isNotEmpty) gap(tagRow(null, loose, cs)),
          ] else
            for (final n in all) gap(row(headline(n), n.tags.isEmpty ? 'UNTAGGED' : n.tags.join(', ').toUpperCase(), cs, () => edit(n))),
          if (all.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Center(child: Text('Nothing here yet. Tap + to add a note.'))),
        ]),
      ),
    );
  }
}
