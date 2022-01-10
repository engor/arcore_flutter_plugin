import 'package:arcore_flutter_plugin/src/arcore_image.dart';
import 'package:arcore_flutter_plugin/src/arcore_text3d.dart';
import 'package:arcore_flutter_plugin/src/arcore_video.dart';
import 'package:arcore_flutter_plugin/src/shape/arcore_shape.dart';
import 'package:arcore_flutter_plugin/src/utils/random_string.dart'
as random_string;
import 'package:arcore_flutter_plugin/src/utils/vector_utils.dart';
import 'package:flutter/widgets.dart';
import 'package:vector_math/vector_math_64.dart';

class ArCoreNode {
  ArCoreNode({
    this.shape,
    this.image,
    this.text3d,
    this.video,
    String name,
    Vector3 position,
    Vector3 scale,
    Vector3 rotation,
    this.children = const [],
  })
      : name = name ?? random_string.randomString(),
        position = ValueNotifier(position),
        scale = ValueNotifier(scale),
        rotation = ValueNotifier(rotation),
        assert(!(shape != null && image != null));

  final List<ArCoreNode> children;

  final ArCoreShape shape;

  final ValueNotifier<Vector3> position;

  final ValueNotifier<Vector3> scale;

  final ValueNotifier<Vector3> rotation;

  final String name;

  final ArCoreImage image;

  final ArCoreText3d text3d;

  final ArCoreVideo video;

  Map<String, dynamic> toMap() =>
      <String, dynamic>{
        'dartType': runtimeType.toString(),
        'shape': shape?.toMap(),
        'position': convertVector3ToMap(position.value),
        'scale': convertVector3ToMap(scale.value),
        'rotation': convertVector3ToMap(rotation.value),
        'name': name,
        'image': image?.toMap(),
        'text3d': text3d?.toMap(),
        'video': video?.toMap(),
        'children':
        this.children.map((arCoreNode) => arCoreNode.toMap()).toList(),
      }
        ..removeWhere((String k, dynamic v) => v == null);
}
