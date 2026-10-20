import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'sticker.dart';
import 'store.dart';

/// The Collection tab: photos in, die-cut stickers out.
class CollectionView extends StatefulWidget {
  const CollectionView({super.key, required this.store});
  final Store store;
  @override
  State<CollectionView> createState() => _CollectionViewState();
}

class _CollectionViewState extends State<CollectionView> {
  bool busy = false;

  Future<void> add(bool camera) async {
    final picker = ImagePicker();
    List<XFile> files;
    if (camera) {
      final x = await picker.pickImage(source: ImageSource.camera);
      files = x == null ? [] : [x];
    } else {
      files = await picker.pickMultiImage();
    }
    if (files.isEmpty || !mounted) return;
    setState(() => busy = true);
    var made = 0;
    String? err;
    for (final f in files) {
      try {
        for (final p in await makeStickers(f.path, widget.store.dir)) {
          widget.store.stickers.insert(0, Sticker(p.split('/').last, p));
          made++;
        }
      } catch (e) {
        err = '$e';
      }
    }
    widget.store.save();
    if (!mounted) return;
    setState(() => busy = false);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(made > 0
          ? 'Made $made sticker${made == 1 ? '' : 's'}'
          : 'No stickers made. ${err ?? 'No clear subject found.'} If this is the first run, the model may still be downloading: try again in a minute.'),
    ));
  }

  void view(Sticker s) => showDialog(
        context: context,
        builder: (ctx) => Dialog(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Flexible(child: Image.file(File(s.path))),
              const SizedBox(height: 12),
              TextButton.icon(
                onPressed: () {
                  Navigator.pop(ctx);
                  widget.store.stickers.remove(s);
                  try {
                    File(s.path).deleteSync();
                  } catch (_) {}
                  widget.store.save();
                  setState(() {});
                },
                icon: const Icon(Icons.delete_outline),
                label: const Text('Delete'),
              ),
            ]),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final st = widget.store.stickers;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Expanded(child: FilledButton.icon(onPressed: busy ? null : () => add(true), icon: const Icon(Icons.photo_camera_outlined), label: const Text('TAKE PHOTO'))),
        const SizedBox(width: 10),
        Expanded(child: OutlinedButton.icon(onPressed: busy ? null : () => add(false), icon: const Icon(Icons.photo_library_outlined), label: const Text('FROM GALLERY'))),
      ]),
      if (busy) const Padding(padding: EdgeInsets.only(top: 14), child: LinearProgressIndicator()),
      if (busy) const Padding(padding: EdgeInsets.only(top: 6), child: Text('Cutting stickers…')),
      const SizedBox(height: 16),
      if (st.isEmpty && !busy)
        const Padding(padding: EdgeInsets.all(24), child: Center(child: Text('Take a photo or pick some. Objects, pets and people become stickers.', textAlign: TextAlign.center)))
      else
        GridView.count(
          crossAxisCount: 3,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [
            for (final s in st)
              GestureDetector(
                onTap: () => view(s),
                child: Transform.rotate(angle: ((s.id.hashCode % 7) - 3) * .03, child: Image.file(File(s.path), fit: BoxFit.contain, cacheWidth: 400)),
              ),
          ],
        ),
    ]);
  }
}
