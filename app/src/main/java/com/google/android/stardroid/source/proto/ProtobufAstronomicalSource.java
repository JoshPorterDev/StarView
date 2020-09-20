// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.source.proto;

import android.content.res.Resources;
import android.util.Log;

import com.google.android.stardroid.R;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.LineSource;
import com.google.android.stardroid.source.PointSource;
import com.google.android.stardroid.source.TextSource;
import com.google.android.stardroid.source.impl.LineSourceImpl;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceProto.LineElementProto;
import com.google.android.stardroid.source.proto.SourceProto.PointElementProto;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the
 * {@link com.google.android.stardroid.source.AstronomicalSource} interface
 * from objects serialized as protocol buffers.
 *
 * @author Brent Bryan
 */
public class ProtobufAstronomicalSource extends AbstractAstronomicalSource {
  private static final String TAG = MiscUtil.getTag(ProtobufAstronomicalSource.class);

  private static final Map<SourceProto.Shape, PointSource.Shape> shapeMap =
    new HashMap<SourceProto.Shape, PointSource.Shape>();

  static {
    shapeMap.put(SourceProto.Shape.CIRCLE, PointSource.Shape.CIRCLE);
    shapeMap.put(SourceProto.Shape.STAR, PointSource.Shape.CIRCLE);
    shapeMap.put(SourceProto.Shape.ELLIPTICAL_GALAXY, PointSource.Shape.ELLIPTICAL_GALAXY);
    shapeMap.put(SourceProto.Shape.SPIRAL_GALAXY, PointSource.Shape.SPIRAL_GALAXY);
    shapeMap.put(SourceProto.Shape.IRREGULAR_GALAXY, PointSource.Shape.IRREGULAR_GALAXY);
    shapeMap.put(SourceProto.Shape.LENTICULAR_GALAXY, PointSource.Shape.LENTICULAR_GALAXY);
    shapeMap.put(SourceProto.Shape.GLOBULAR_CLUSTER, PointSource.Shape.GLOBULAR_CLUSTER);
    shapeMap.put(SourceProto.Shape.OPEN_CLUSTER, PointSource.Shape.OPEN_CLUSTER);
    shapeMap.put(SourceProto.Shape.NEBULA, PointSource.Shape.NEBULA);
    shapeMap.put(SourceProto.Shape.HUBBLE_DEEP_FIELD, PointSource.Shape.HUBBLE_DEEP_FIELD);
  }

  private final AstronomicalSourceProto proto;
  private final Resources resources;

  // Lazily construct the names.
  private ArrayList<String> names;

  public ProtobufAstronomicalSource(AstronomicalSourceProto proto, Resources resources) {
    this.proto = proto;
    this.resources = resources;
    // Not ideal to be doing this in the constructor. TODO(john): investigate which threads
    // this is all happening on.
    proto = processStringIds(proto);
  }

  /**
   * The data files contain only the text version of the string Ids. Looking them up
   * by this id will be expensive so precalculate any integer ids. See the datageneration
   * design doc for an explanation.
   */
  private static AstronomicalSourceProto processStringIds(AstronomicalSourceProto proto) {
    AstronomicalSourceProto.Builder processed = proto.toBuilder();
    for (String strId : proto.getNameStrIdsList()) {
      processed.addNameIntIds(toInt(strId));
    }
    for (LabelElementProto.Builder label : processed.getLabelBuilderList()) {
      label.setStringsIntId(toInt(label.getStringsStrId()));
    }
    Log.d(TAG, "Processed " + processed.getLabelList());
    return processed.build();
  }

  private static int toInt(String stringId) {
    Log.d(TAG, stringId + R.string.missing_label);
    return R.string.missing_label;
  }

  @Override
  public synchronized ArrayList<String> getNames() {
    if (names == null) {
      names = new ArrayList<String>(proto.getNameIntIdsCount());
      for (int id : proto.getNameIntIdsList()) {
        names.add(resources.getString(id));
      }
    }
    return names;
  }

  @Override
  public GeocentricCoordinates getSearchLocation() {
    return getCoords(proto.getSearchLocation());
  }

  @Override
  public List<PointSource> getPoints() {
    if (proto.getPointCount() == 0) {
      return Collections.<PointSource>emptyList();
    }
    ArrayList<PointSource> points = new ArrayList<PointSource>(proto.getPointCount());
    for (PointElementProto element : proto.getPointList()) {
      points.add(new PointSourceImpl(getCoords(element.getLocation()),
          element.getColor(), element.getSize(), shapeMap.get(element.getShape())));
    }
    return points;
  }

  @Override
  public List<TextSource> getLabels() {
    if (proto.getLabelCount() == 0) {
      return Collections.<TextSource>emptyList();
    }
    ArrayList<TextSource> points = new ArrayList<TextSource>(proto.getLabelCount());
    for (LabelElementProto element : proto.getLabelList()) {
      Log.d(TAG, "Label " + element.getStringsIntId() + " : " + element.getStringsStrId());
      points.add(new TextSourceImpl(getCoords(element.getLocation()),
          resources.getString(R.string.missing_label), //element.getStringsIntId()),
          element.getColor(), element.getOffset(), element.getFontSize()));
    }
    return points;

  }

  @Override
  public List<LineSource> getLines() {
    if (proto.getLineCount() == 0) {
      return Collections.<LineSource>emptyList();
    }
    ArrayList<LineSource> points = new ArrayList<LineSource>(proto.getLineCount());
    for (LineElementProto element : proto.getLineList()) {
      ArrayList<GeocentricCoordinates> vertices =
          new ArrayList<GeocentricCoordinates>(element.getVertexCount());
      for (GeocentricCoordinatesProto elementVertex : element.getVertexList()) {
        vertices.add(getCoords(elementVertex));
      }
      points.add(new LineSourceImpl(element.getColor(), vertices, element.getLineWidth()));
    }
    return points;
  }

  private static GeocentricCoordinates getCoords(GeocentricCoordinatesProto proto) {
    return GeocentricCoordinates.getInstance(proto.getRightAscension(), proto.getDeclination());
  }
}
