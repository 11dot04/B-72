import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart' show Rect;
import 'package:path_provider/path_provider.dart';
import 'main.dart' show Kind, NoteCard;
import 'sticker.dart';
import 'templates.dart';

class Block {
  Block(this.kind, {
    this.id = '', 
    this.title = '', 
    this.body = '', 
    this.path, 
    this.focal = const Rect.fromLTRB(.3, .3, .7, .7), 
    this.aspect = 1, 
    List<String>? items
  }) : items = items ?? [];
  
  Kind kind;
  String id, title, body;
  String? path;
  Rect focal;
  double aspect;
  List<String> items;

  Map toJson() => {
    'i': id, 
    'k': kind.index, 
    't': title, 
    'b': body, 
    'p': path, 
    'f': [focal.left, focal.top, focal.right, focal.bottom], 
    'a': aspect, 
    'items': items
  };

  factory Block.fromJson(Map j) {
    final f = (j['f'] as List).map((e) => (e as num).toDouble()).toList();
    return Block(
      Kind.values[j['k']], 
      id: j['i'] ?? '',
      title: j['t'], 
      body: j['b'], 
      path: j['p'], 
      focal: Rect.fromLTRB(f[0], f[1], f[2], f[3]), 
      aspect: (j['a'] as num).toDouble(),
      items: (j['items'] as List?)?.map((e) => e as String).toList()
    );
  }
}

class Place {
  Place(this.x, this.y);
  double x, y;
}

/// A note is an ordered list of blocks. Its position on each tag's canvas
/// is stored per tag, so the same note can sit differently on different canvases.
class Note {
  Note(this.id, {List<Block>? blocks, Set<String>? tags, Map<String, Place>? places})
      : blocks = blocks ?? [],
        tags = tags ?? {},
        places = places ?? {};
  final String id;
  List<Block> blocks;
  Set<String> tags;
  Map<String, Place> places;

  /// The block a canvas card shows: first photo, else first block.
  Block? get cover => blocks.isEmpty ? null : blocks.firstWhere((b) => b.kind == Kind.photo, orElse: () => blocks.first);

  Map toJson() => {
        'id': id,
        'blocks': blocks.map((b) => b.toJson()).toList(),
        'tags': tags.toList(),
        'places': places.map((k, v) => MapEntry(k, [v.x, v.y])),
      };

  factory Note.fromJson(Map j) => Note(
        j['id'],
        blocks: (j['blocks'] as List).map((b) => Block.fromJson(b)).toList(),
        tags: (j['tags'] as List).map((t) => t as String).toSet(),
        places: (j['places'] as Map).map((k, v) => MapEntry(k as String, Place((v[0] as num).toDouble(), (v[1] as num).toDouble()))),
      );
}

/// Simple JSON-file storage. Swap for SQLite later if it outgrows this.
class Store {
  Store(this.file, this.dir, this.notes, this.stickers);
  final File file;
  final Directory dir;
  final List<Note> notes;
  final List<Sticker> stickers;

  static Future<Store> load() async {
    final d = await getApplicationDocumentsDirectory();
    final dir = Directory('${d.path}/photos')..createSync(recursive: true);
    final f = File('${d.path}/notes.json');
    var notes = <Note>[];
    final stickers = <Sticker>[];
    try {
      if (f.existsSync()) {
        final j = jsonDecode(f.readAsStringSync());
        notes = (j is List ? j : j['notes'] as List).map((x) => Note.fromJson(x)).toList();
        if (j is Map) stickers.addAll((j['stickers'] as List).map((x) => Sticker.fromJson(x)));
      }
    } catch (_) {}
    for (final n in notes) {
      for (var i = 0; i < n.blocks.length; i++) {
        if (n.blocks[i].id.isEmpty) n.blocks[i].id = '${n.id}_$i';
      }
    }
    return Store(f, dir, notes, stickers);
  }

  void save() => file.writeAsStringSync(jsonEncode({'notes': notes.map((n) => n.toJson()).toList(), 'stickers': stickers.map((s) => s.toJson()).toList()}));

  Set<String> get tags => {for (final n in notes) ...n.tags};
  int count(String tag) => notes.where((n) => n.tags.contains(tag)).length;

  Future<String> keepPhoto(String src) async => (await File(src).copy('${dir.path}/${DateTime.now().microsecondsSinceEpoch}.jpg')).path;

  void setPlace(String tag, String blockId, double x, double y) {
    for (final n in notes) {
      if (n.blocks.any((b) => b.id == blockId)) n.places['$tag|$blockId'] = Place(x, y);
    }
  }

  /// Photos on a tag's canvas (used to derive its palette).
  List<String> photosFor(String tag) => [
        for (final n in notes)
          if (n.tags.contains(tag))
            for (final b in n.blocks)
              if (b.kind == Kind.photo && b.path != null) b.path!,
      ].take(4).toList();

  /// One canvas card per block of every note with this tag. Unplaced cards
  /// start at the top and are moved to a free spot by the canvas's tidy pass.
  List<NoteCard> cardsFor(String tag) => [
        for (final n in notes)
          if (n.tags.contains(tag))
            for (var i = 0; i < n.blocks.length; i++) cardOf(n, n.blocks[i], i, tag),
      ];

  NoteCard cardOf(Note n, Block b, int i, String tag) {
    final p = n.places['$tag|${b.id}'], photo = b.kind == Kind.photo;
    return NoteCard(b.id, b.kind, p?.x ?? .3, p?.y ?? 0, photo ? .38 : tplWidth(b.kind), photo ? b.aspect : 0,
        noteId: n.id,
        order: i,
        angle: photo ? ((b.id.hashCode % 9) - 4) * .01 : 0,
        focal: b.focal,
        path: b.path,
        title: b.title,
        titleSize: b.kind == Kind.bare ? .07 : .05,
        body: b.body);
  }
}
