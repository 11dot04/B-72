import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'main.dart' show Kind, detectFocus;
import 'store.dart';

class _Ed {
  _Ed(this.b)
      : t = TextEditingController(text: b.title),
        x = TextEditingController(text: b.body);
  final Block b;
  final TextEditingController t, x;
}

class Editor extends StatefulWidget {
  const Editor({super.key, required this.store, this.note, this.tag});
  final Store store;
  final Note? note;
  final String? tag; // pre-filled tag for new notes
  @override
  State<Editor> createState() => _EditorState();
}

class _EditorState extends State<Editor> {
  late final List<_Ed> eds = (widget.note?.blocks ?? <Block>[]).map(_Ed.new).toList();
  late final TextEditingController tags =
      TextEditingController(text: (widget.note?.tags ?? <String>{if (widget.tag != null) widget.tag!}).join(', '));

  void add(Kind k) => setState(() => eds.add(_Ed(Block(k))));

  Future<void> addPhoto() async {
    final f = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (f == null) return;
    final path = await widget.store.keepPhoto(f.path);
    final img = await decodeImageFromList(await File(path).readAsBytes());
    final ai = img.height / img.width, ac = ai.clamp(.6, 1.4).toDouble();
    var fo = await detectFocus(path, Size(img.width.toDouble(), img.height.toDouble()));
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
    setState(() => eds.add(_Ed(Block(Kind.photo, path: path, focal: fo, aspect: ac))));
  }

  void save() {
    for (final e in eds) {
      e.b.title = e.t.text.trim();
      e.b.body = e.x.text.trim();
    }
    final blocks = eds.map((e) => e.b).where((b) => b.kind == Kind.photo || b.title.isNotEmpty || b.body.isNotEmpty).toList();
    if (blocks.isEmpty) return Navigator.pop(context);
    final tg = tags.text.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();
    final s = widget.store;
    final n = widget.note ?? Note(DateTime.now().microsecondsSinceEpoch.toString());
    n.blocks = blocks;
    n.tags = tg;
    n.places.removeWhere((k, _) => !tg.contains(k));
    if (!s.notes.contains(n)) s.notes.add(n);
    s.save();
    Navigator.pop(context);
  }

  Widget block(_Ed e) => Card(
        margin: const EdgeInsets.only(bottom: 12),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Text(e.b.kind == Kind.bare ? 'TEXT ON CANVAS' : e.b.kind.name.toUpperCase(), style: Theme.of(context).textTheme.labelSmall),
              const Spacer(),
              IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => eds.remove(e))),
            ]),
            if (e.b.kind == Kind.photo)
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Image.file(File(e.b.path!), height: 180, width: double.infinity, fit: BoxFit.cover, cacheWidth: 800),
              )
            else ...[
              TextField(controller: e.t, decoration: const InputDecoration(labelText: 'Title')),
              if (e.b.kind != Kind.bare)
                TextField(
                  controller: e.x,
                  minLines: 2,
                  maxLines: 8,
                  decoration: InputDecoration(labelText: e.b.kind == Kind.checklist ? 'One item per line (start with x if done)' : 'Text'),
                ),
            ],
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.note == null ? 'New note' : 'Note'), actions: [
          if (widget.note != null)
            IconButton(
              tooltip: 'Delete',
              icon: const Icon(Icons.delete_outline),
              onPressed: () {
                widget.store.notes.remove(widget.note);
                widget.store.save();
                Navigator.pop(context);
              },
            ),
          IconButton(tooltip: 'Save', icon: const Icon(Icons.check), onPressed: save),
        ]),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          TextField(controller: tags, decoration: const InputDecoration(labelText: 'Tags (comma separated)', prefixIcon: Icon(Icons.sell_outlined))),
          const SizedBox(height: 12),
          for (final e in eds) block(e),
          Wrap(spacing: 8, children: [
            ActionChip(avatar: const Icon(Icons.notes, size: 18), label: const Text('Text'), onPressed: () => add(Kind.text)),
            ActionChip(avatar: const Icon(Icons.checklist, size: 18), label: const Text('Checklist'), onPressed: () => add(Kind.checklist)),
            ActionChip(avatar: const Icon(Icons.title, size: 18), label: const Text('Text on canvas'), onPressed: () => add(Kind.bare)),
            ActionChip(avatar: const Icon(Icons.photo_outlined, size: 18), label: const Text('Photo'), onPressed: addPhoto),
          ]),
        ]),
      );
}
