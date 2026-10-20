import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'main.dart' show Kind, detectFocus;
import 'store.dart';
import 'templates.dart';

class _Extra {
  _Extra(this.kind, this.b)
      : t = TextEditingController(text: b.title),
        x = TextEditingController(text: b.body);
  final Kind kind;
  final Block b;
  final TextEditingController t, x;
}

/// One-screen composer: title, text, checklist, photos and tags all at once.
class Editor extends StatefulWidget {
  const Editor({super.key, required this.store, this.note, this.tag});
  final Store store;
  final Note? note;
  final String? tag; // pre-filled tag for new notes
  @override
  State<Editor> createState() => _EditorState();
}

class _EditorState extends State<Editor> {
  final title = TextEditingController(), body = TextEditingController(), items = TextEditingController(), tags = TextEditingController();
  final photos = <Block>[];
  Block? bareB, textB, listB; // existing blocks are reused so their canvas positions survive
  bool busy = false, smart = false;
  final extras = <_Extra>[];
  int _c = 0;
  String fresh() => '${DateTime.now().microsecondsSinceEpoch}_${_c++}';

  @override
  void initState() {
    super.initState();
    final n = widget.note;
    tags.text = (n?.tags ?? <String>{if (widget.tag != null) widget.tag!}).join(', ');
    for (final b in n?.blocks ?? <Block>[]) {
      if (b.kind == Kind.photo) {
        photos.add(b);
      } else if (b.kind == Kind.bare && bareB == null) {
        bareB = b;
        title.text = b.title;
      } else if (b.kind == Kind.checklist && listB == null) {
        listB = b;
        items.text = b.body;
      } else if (b.kind != Kind.poster && isTpl(b.kind)) {
        extras.add(_Extra(b.kind, b));
      } else if (textB == null) {
        textB = b;
        smart = b.kind == Kind.poster;
        body.text = b.body;
        if (title.text.isEmpty) title.text = b.title;
      }
    }
  }

  Future<Block> photoBlock(String src) async {
    final path = await widget.store.keepPhoto(src);
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
    return Block(Kind.photo, id: fresh(), path: path, focal: fo, aspect: ac);
  }

  Future<void> addPhotos() async {
    final fs = await ImagePicker().pickMultiImage();
    if (fs.isEmpty) return;
    setState(() => busy = true);
    for (final f in fs) {
      try {
        photos.add(await photoBlock(f.path));
        if (mounted) setState(() {});
      } catch (_) {}
    }
    if (mounted) setState(() => busy = false);
  }

  void save() {
    final s = widget.store;
    final n = widget.note ?? Note('${DateTime.now().microsecondsSinceEpoch}');
    Block upsert(Block? old, Kind k, {String t = '', String b = ''}) => (old ?? Block(k, id: fresh()))
      ..kind = k
      ..title = t
      ..body = b;
    final t = title.text.trim(), tx = body.text.trim(), it = items.text.trim();
    final blocks = <Block>[
      if (t.isNotEmpty) upsert(bareB, Kind.bare, t: t),
      if (tx.isNotEmpty) upsert(textB, smart ? Kind.poster : Kind.text, b: tx),
      if (it.isNotEmpty) upsert(listB, Kind.checklist, t: (listB?.title.isNotEmpty ?? false) ? listB!.title : 'List', b: it),
      for (final e in extras)
        if (e.t.text.trim().isNotEmpty || e.x.text.trim().isNotEmpty) upsert(e.b, e.kind, t: e.t.text.trim(), b: e.x.text.trim()),
      ...photos,
    ];
    if (blocks.isEmpty) return Navigator.pop(context);
    n.blocks = blocks;
    n.tags = tags.text.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toSet();
    if (!s.notes.contains(n)) s.notes.add(n);
    s.save();
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    Widget label(String t) => Padding(padding: const EdgeInsets.only(top: 20, bottom: 6), child: Text(t, style: Theme.of(context).textTheme.labelSmall));
    InputDecoration box(String h) => InputDecoration(
        hintText: h, filled: true, fillColor: cs.secondaryContainer.withAlpha(110), border: OutlineInputBorder(borderRadius: BorderRadius.circular(20), borderSide: BorderSide.none));
    const hints = {
      Kind.quote: ('Who said it', 'Quote'),
      Kind.stat: ('What it measures', 'Figure, e.g. 319 ms'),
      Kind.info: ('Heading', 'One per line, like Where: Echo Bridge'),
      Kind.link: ('Title', 'https://...'),
    };
    Widget extraCard(_Extra e) => Card(
          margin: const EdgeInsets.only(bottom: 12),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Text(tplLabel(e.kind), style: Theme.of(context).textTheme.labelSmall),
                const Spacer(),
                IconButton(icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => extras.remove(e))),
              ]),
              TextField(controller: e.x, minLines: e.kind == Kind.quote || e.kind == Kind.info ? 2 : 1, maxLines: 8, decoration: InputDecoration(hintText: hints[e.kind]!.$2)),
              TextField(controller: e.t, decoration: InputDecoration(hintText: hints[e.kind]!.$1)),
            ]),
          ),
        );
    Widget thumb(Block p) => Stack(children: [
          ClipRRect(borderRadius: BorderRadius.circular(16), child: Image.file(File(p.path!), width: 96, height: 96, fit: BoxFit.cover, cacheWidth: 300)),
          Positioned(
            top: 4,
            right: 4,
            child: GestureDetector(
              onTap: () => setState(() => photos.remove(p)),
              child: const CircleAvatar(radius: 11, backgroundColor: Colors.black54, child: Icon(Icons.close, size: 14, color: Colors.white)),
            ),
          ),
        ]);
    return Scaffold(
      appBar: AppBar(title: Text(widget.note == null ? 'New note' : 'Edit note'), actions: [
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
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 40), children: [
        TextField(
          controller: title,
          textCapitalization: TextCapitalization.sentences,
          style: const TextStyle(fontFamily: 'serif', fontSize: 30, height: 1.1),
          decoration: const InputDecoration(border: InputBorder.none, hintText: 'Title'),
        ),
        label('TEXT'),
        TextField(controller: body, minLines: 3, maxLines: 10, decoration: box('Write something')),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('Smart layout'),
          subtitle: const Text('Finds dates, places, prices and figures in the text and lays them out as a poster'),
          value: smart,
          onChanged: (v) => setState(() => smart = v),
        ),
        label('CHECKLIST'),
        TextField(controller: items, minLines: 2, maxLines: 10, decoration: box('One item per line (start with x if done)')),
        label('PHOTOS'),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (final p in photos) thumb(p),
          GestureDetector(
            onTap: busy ? null : addPhotos,
            child: Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(color: cs.primaryContainer, borderRadius: BorderRadius.circular(16)),
              child: busy ? const Center(child: SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))) : const Icon(Icons.add_photo_alternate_outlined),
            ),
          ),
        ]),
        label('MORE'),
        for (final e in extras) extraCard(e),
        Wrap(spacing: 8, children: [
          for (final k in [Kind.quote, Kind.stat, Kind.info, Kind.link])
            ActionChip(label: Text(tplLabel(k)), onPressed: () => setState(() => extras.add(_Extra(k, Block(k, id: fresh()))))),
        ]),
        label('TAGS'),
        TextField(controller: tags, decoration: box('Comma separated')),
      ]),
    );
  }
}
