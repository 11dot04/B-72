import 'dart:io';
import 'package:flutter/material.dart';
import 'editor.dart';
import 'main.dart' show Board, Kind;
import 'store.dart';

class Home extends StatefulWidget {
  const Home({super.key, required this.store, required this.onSeed});
  final Store store;
  final VoidCallback onSeed;
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  String q = '';

  Future<void> edit([Note? n]) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Editor(store: widget.store, note: n)));
    setState(() {});
  }

  Future<void> openTag(String t) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => Board(store: widget.store, tag: t, onSeed: widget.onSeed)));
    setState(() {});
  }

  String headline(Note n) => n.blocks.map((b) => b.title.isNotEmpty ? b.title : b.body).firstWhere((s) => s.isNotEmpty, orElse: () => 'Photo');

  @override
  Widget build(BuildContext context) {
    final s = widget.store, cs = Theme.of(context).colorScheme, query = q.toLowerCase();
    final notes = s.notes
        .where((n) =>
            query.isEmpty ||
            n.tags.any((t) => t.toLowerCase().contains(query)) ||
            n.blocks.any((b) => '${b.title} ${b.body}'.toLowerCase().contains(query)))
        .toList()
        .reversed
        .toList();
    final tags = s.tags.where((t) => query.isEmpty || t.toLowerCase().contains(query)).toList()..sort();
    return Scaffold(
      appBar: AppBar(title: const Text('Notes'), actions: [
        IconButton(tooltip: 'Next palette', icon: const Icon(Icons.palette_outlined), onPressed: widget.onSeed),
      ]),
      floatingActionButton: FloatingActionButton(onPressed: () => edit(), child: const Icon(Icons.add)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 96), children: [
        TextField(
          onChanged: (v) => setState(() => q = v),
          decoration: InputDecoration(
            prefixIcon: const Icon(Icons.search),
            hintText: 'Search notes and tags',
            filled: true,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none),
          ),
        ),
        if (tags.isNotEmpty) ...[
          const Padding(padding: EdgeInsets.only(top: 20, bottom: 8), child: Text('Tags')),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final t in tags) ActionChip(label: Text('$t · ${s.count(t)}'), onPressed: () => openTag(t)),
          ]),
        ],
        const Padding(padding: EdgeInsets.only(top: 20, bottom: 8), child: Text('Notes')),
        if (notes.isEmpty) const Text('Nothing here yet. Tap + to add a note.'),
        for (final n in notes)
          Card(
            elevation: 0,
            color: cs.secondaryContainer,
            child: ListTile(
              leading: n.cover?.kind == Kind.photo && n.cover!.path != null
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.file(File(n.cover!.path!), width: 48, height: 48, fit: BoxFit.cover, cacheWidth: 150),
                    )
                  : const Icon(Icons.notes),
              title: Text(headline(n), maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text('${n.blocks.length} block${n.blocks.length == 1 ? '' : 's'} · ${n.tags.isEmpty ? 'untagged' : n.tags.join(', ')}'),
              onTap: () => edit(n),
            ),
          ),
      ]),
    );
  }
}
