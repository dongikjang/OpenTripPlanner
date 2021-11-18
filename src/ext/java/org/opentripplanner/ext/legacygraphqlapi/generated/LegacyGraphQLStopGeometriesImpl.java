package org.opentripplanner.ext.legacygraphqlapi.generated;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import org.locationtech.jts.geom.GeometryCollection;
import org.opentripplanner.ext.legacygraphqlapi.generated.LegacyGraphQLDataFetchers.LegacyGraphQLStopGeometries;
import org.opentripplanner.util.PolylineEncoder;
import org.opentripplanner.util.model.EncodedPolylineBean;

public class LegacyGraphQLStopGeometriesImpl implements LegacyGraphQLStopGeometries {

    @Override
    public DataFetcher<String> geoJson() {
        return env -> {
            var geoms = getSource(env);
            return geoms.toString();
        };
    }

    @Override
    public DataFetcher<Iterable<EncodedPolylineBean>> polylines() {
        return env -> {
            var geometries = getSource(env);
            var polylines = new ArrayList<EncodedPolylineBean>();

            for(int i = 0; i < geometries.getNumGeometries(); i++) {
               var geom = geometries.getGeometryN(i);
               var polyline = PolylineEncoder.createEncodings(geom);
               polylines.add(polyline);
            }
            return polylines;
        };
    }

    private GeometryCollection getSource(DataFetchingEnvironment environment) {
        return environment.getSource();
    }
}
