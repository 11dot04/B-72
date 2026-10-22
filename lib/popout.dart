import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'cutout.dart';
import 'store.dart';

/// A single photo with one clear subject: it sits in an expressive shape and the subject breaks out of
/// it in exactly one place, the top. The sides and base of the subject are cropped by the shape.
bool canPopOut(Block b, {int photos = 1}) => photos == 1 && cutPath(b) != null && cutFrac(b) >= .1 && cutFrac(b) <= .65;

/// Where the shape starts, given the subject's rect inside the area: roughly the top fifth of the subject
/// (the head of a portrait, the foam of a latte) stays above it. Tall, narrow subjects overflow a little less.
double shapeTop(Rect s) => s.top + s.height * (s.height / max(1, s.width) > 1.7 ? .14 : .22);

class _Shape extends CustomClipper<Path> {
  _Shape(this.kind, this.top);
  final int kind;
  final double top;

  @override
  Path getClip(Size s) {
    final h = s.height - top, w = s.width;
    switch (kind % 4) {
      case 0: // arch
        return Path()..addRRect(RRect.fromLTRBAndCorners(0, top, w, s.height, topLeft: Radius.circular(w / 2), topRight: Radius.circular(w / 2), bottomLeft: const Radius.circular(36), bottomRight: const Radius.circular(36)));
      case 1: // cookie
        final p = Path(), c = Offset(w / 2, top + h / 2), r = min(w, h) / 2 * .96;
        for (var i = 0; i <= 240; i++) {
          final a = i / 240 * 2 * pi, o = c + Offset(cos(a), sin(a)) * (r * (.9 + .1 * cos(9 * a)));
          i == 0 ? p.moveTo(o.dx, o.dy) : p.lineTo(o.dx, o.dy);
        }
        return p..close();
      case 2: // pill
        return Path()..addRRect(RRect.fromLTRBR(w * .08, top, w * .92, s.height, Radius.circular(w * .42)));
      default: // leaf
        return Path()..addRRect(RRect.fromLTRBAndCorners(0, top, w, s.height, topLeft: const Radius.circular(40), topRight: const Radius.circular(150), bottomLeft: const Radius.circular(150), bottomRight: const Radius.circular(40)));
    }
  }

  @override
  bool shouldReclip(_Shape old) => old.kind != kind || old.top != top;
}

/// Everything the shape covers plus the open strip above it, so the subject only escapes at the top.
class _Open extends CustomClipper<Path> {
  _Open(this.shape);
  final _Shape shape;
  @override
  Path getClip(Size s) => Path.combine(PathOperation.union, shape.getClip(s), Path()..addRect(Rect.fromLTWH(0, 0, s.width, shape.top)));
  @override
  bool shouldReclip(_Open old) => old.shape.shouldReclip(shape);
}

/// Rect of the subject inside an area of [size] (the photo is drawn with BoxFit.cover, centred).
Rect? subjectIn(Block b, Size size) => cutRectIn(b, size, Alignment.center);

class PopOut extends StatelessWidget {
  const PopOut({super.key, required this.block, required this.fill, required this.tint, required this.shape});
  final Block block;
  final Color fill;
  final Color? tint;
  final int shape;

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (_, c) {
        final size = Size(c.maxWidth, c.maxHeight), s = subjectIn(block, size) ?? Rect.fromLTWH(size.width * .2, size.height * .1, size.width * .6, size.height * .7);
        final clip = _Shape(shape, shapeTop(s).clamp(size.height * .08, size.height * .6).toDouble());
        Widget img(String p) => Image.file(File(p), fit: BoxFit.cover, width: double.infinity, height: double.infinity, cacheWidth: 1100);
        Widget tinted(Widget w) => tint == null ? w : ColorFiltered(colorFilter: ColorFilter.mode(tint!, BlendMode.color), child: w);
        return Stack(fit: StackFit.expand, children: [
          ClipPath(clipper: clip, child: ColoredBox(color: fill)),
          ClipPath(clipper: clip, child: tinted(img(block.path!))),
          ClipPath(clipper: _Open(clip), child: tinted(img(cutPath(block)!))),
        ]);
      });
}
