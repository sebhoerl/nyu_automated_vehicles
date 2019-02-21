package edu.nyu.matsim.automated_vehicles.operating_area;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;

public class ShpFilter {
	final private SimpleFeatureCollection shapes;

	public ShpFilter(SimpleFeatureCollection shapes) {
		this.shapes = shapes;
	}

	public Collection<Id<Link>> filter(Network network) {
		Collection<Id<Link>> containedLinks = new HashSet<>();

		GeometryFactory factory = new GeometryFactory();

		for (Link link : network.getLinks().values()) {
			SimpleFeatureIterator iterator = shapes.features();

			while (iterator.hasNext()) {
				MultiPolygon polygon = (MultiPolygon) iterator.next().getDefaultGeometry();

				if (polygon.contains(
						factory.createPoint(new Coordinate(link.getCoord().getX(), link.getCoord().getY())))) {
					containedLinks.add(link.getId());
					break;
				}
			}
		}

		return containedLinks;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static public ShpFilter create(File path) throws IOException {
		Map inputMap = new HashMap<>();
		inputMap.put("url", path.toURI().toURL());
		DataStore dataStore = DataStoreFinder.getDataStore(inputMap);

		SimpleFeatureSource featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]);
		SimpleFeatureCollection collection = DataUtilities.collection(featureSource.getFeatures());
		dataStore.dispose();

		return new ShpFilter(collection);
	}
}
