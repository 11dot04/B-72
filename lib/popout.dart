import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';

/// Expressive shapes (arch, cookie, pill, squircle with a bite). They start a little
/// below the top edge so a subject can break out of them there.
class _Shape extends CustomClipper<Path> {
  _Shape(this.kind);
  final int kind;

  @override
  Path getClip(Size s) {
    final top = s.height * .2, h = s.height - top, w = s.width;
    switch (kind % 4) {
      case 0:
        return Path()..addRRect(RRect.fromLTRBAndCorners(0, top, w, s.height, topLeft: Radius.circular(w / 2), topRight: Radius.circular(w / 2), bottomLeft: const Radius.circular(36), bottomRight: const Radius.circular(36)));
      case 1:
        final p = Path(), c = Offset(w / 2, top + h / 2), r = min(w, h) / 2 * .96;
        for (var i = 0; i <= 240; i++) {
          final a = i / 240 * 2 * pi, o = c + Offset(cos(a), sin(a)) * (r * (.9 + .1 * cos(9 * a)));
          i == 0 ? p.moveTo(o.dx, o.dy) : p.lineTo(o.dx, o.dy);
        }
        return p..close();
      case 2:
        return Path()..addRRect(RRect.fromLTRBR(w * .08, top, w * .92, s.height, Radius.circular(w * .42)));
      default:
        return Path()..addRRect(RRect.fromLTRBAndCorners(0, top, w, s.height, topLeft: const Radius.circular(40), topRight: const Radius.circular(150), bottomLeft: const Radius.circular(150), bottomRight: const Radius.circular(40)));
    }
  }

  @override
  bool shouldReclip(_Shape old) => old.kind != kind;
}

/// The photo lives inside a shape; the cut-out subject is drawn over it, unclipped.
class PopOut extends StatelessWidget {
  const PopOut({super.key, required this.photo, required this.cut, required this.fill, required this.tint, required this.shape});
  final String photo, cut;
  final Color fill;
  final Color? tint;
  final int shape;

  @override
  Widget build(BuildContext context) {
    final clip = _Shape(shape);
    Widget img(String p) => Image.file(File(p), fit: BoxFit.cover, width: double.infinity, height: double.infinity, cacheWidth: 1100);
    return Stack(fit: StackFit.expand, children: [
      ClipPath(clipper: clip, child: ColoredBox(color: fill)),
      ClipPath(clipper: clip, child: tint == null ? img(photo) : ColorFiltered(colorFilter: ColorFilter.mode(tint!, BlendMode.color), child: img(photo))),
      img(cut),
    ]);
  }
}
