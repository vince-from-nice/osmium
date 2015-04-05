package org.openstreetmap.osmaxil.plugin.building;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.osmaxil.data.ElementTagNames;
import org.openstreetmap.osmaxil.data.MatchingElementId;
import org.openstreetmap.osmaxil.data.api.OsmApiRoot;
import org.openstreetmap.osmaxil.data.building.BuildingElement;
import org.openstreetmap.osmaxil.data.building.BuildingImport;
import org.openstreetmap.osmaxil.plugin.AbstractPlugin;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractBuildingPlugin extends AbstractPlugin<BuildingElement, BuildingImport> {
 
    @Override
    public BuildingElement createElement(long osmId, long relationId, OsmApiRoot data) {
        BuildingElement element = new BuildingElement(osmId);
        // TODO move below code in AbstractPlugin or AbstractElement since it's generic
        element.setRelationId(relationId);
        element.setApiData(data);
        for(String tagName : this.getUpdatableTagNames()) {
            element.getOrignalValuesByTagNames().put(tagName, element.getTagValue(tagName));
        }
        return element;
    }

    @Override
    public List<MatchingElementId> findMatchingElements(BuildingImport imp) {
        List<MatchingElementId> result = new ArrayList<MatchingElementId>();
        Long[] ids = new Long[0];
        // Find in PostGIS all buildings matching (ie. containing) the coordinates of the import
        BuildingImport building = (BuildingImport) imp;
        if (building.getLat() != null && building.getLon() != null) {
            ids = this.findBuildingIDsByLatLon(building.getLon(), building.getLat());
        } else if (building.getGeometry() != null) {
            ids = this.findBuildingIDsByGeometry(building.getGeometry());
            LOGGER.info("OSM IDs of buildings matching (" + building.getGeometry() + ") : ");
        } else {
            LOGGER.error("Unable to find building because there's no coordinates neither geometry");
        }
        // Parsing the IDs to check if they refers to normal elements (ie. ways) or relations
        StringBuffer sb = new StringBuffer("OSM IDs of matching buildings : [ ");
        for (int i = 0; i < ids.length; i++) {
            MatchingElementId relevantElement = new MatchingElementId();
            // If ID is positive it means it's a normal element (ie. a way)
            if (ids[i] > 0) {
                relevantElement.setOsmId(ids[i]);
                relevantElement.setRelationId(-1);
            } 
            // If ID is negative it means it's a multipolygon relations => need to find its relevant outer member
            else {
                LOGGER.debug("A multipolygon relation has been found (" + ids[i] + "), looking for its relevant outer member");
                relevantElement.setOsmId(this.findRelevantOuterMemberId(- ids[i], imp));
                relevantElement.setRelationId(- ids[i]);
            }
            result.add(relevantElement);
            sb.append(ids[i] + " ");
        }
        LOGGER.info(sb.toString() + "]");
        return result;
    }
    
    @Override
    public boolean isElementTagUpdatable(BuildingElement element, String tagName) {
        // For now all building tags are updatable if it doesn't have an original value
        return element.getOrignalValuesByTagNames().get(tagName) == null;
    }

    @Override
    public boolean updateElementTag(BuildingElement element, String tagName) {
        String tagValue = element.getBestTagValueByTagName(tagName);
        if (tagValue == null) {
            LOGGER.warn("Cannot update tag because best tag value is null for " + tagName);
            return false;
        }
        boolean updated = false;
        if (ElementTagNames.HEIGHT.equals(tagName)) {
            LOGGER.info("===> Updating height to " + tagValue);
            element.setHeight(Float.parseFloat(tagValue));
            updated = true;
        }
        if (ElementTagNames.BUILDING_LEVELS.equals(tagName)) {
            LOGGER.info("===> Updating levels to " + tagValue);
            element.setLevels(Integer.parseInt(tagValue));
            updated = true;
        }
        return updated;
    }
    
    @Override
    public float computeImportMatchingScore(BuildingImport imp) {
        float result = 0f;
        if (imp.getArea() == null) {
            LOGGER.warn("Unable to compute score because import has NO area");
            return 0f;
        }
        if (imp.getElement() == null) {
            LOGGER.warn("Unable to compute score because import has NO element attached");
            return 0f;
        }
        // If the related element belongs to a relation, consider it instead of the element itself (osm2pgsql doesn't store relation members) 
        long elementId = imp.getElement().getOsmId();
        if (imp.getElement().getRelationId() > 0) {
            elementId = - imp.getElement().getRelationId(); // reinverse the ID because osm2pgsql stores relations like that
        }
        int elementArea = this.osmPostgisService.getPolygonAreaById(elementId);
        // TODO cache it for next imports 
        LOGGER.info("Element computed area is [" + elementArea + "]");
        // Compare area between import and element
        if (elementArea > 0) {
         // Returns a float which tends to 1.0 when areas are going closer (and tends to 0.0 if different)
            if ( imp.getArea() < elementArea) {
                result = ((float) imp.getArea() / elementArea);
            } else {
                result = ((float)  elementArea / imp.getArea());
            }
        }
        // TODO Add other criteria such the "centrality" of the import into the element area
        return result;
    }
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private methods 
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    private long findRelevantOuterMemberId(long relationId, BuildingImport imp) {
        long result = 0;
        // Fetch members from PostGIS
        String membersString = this.osmPostgisService.getRelationMembers(relationId);
        // Parse members strings
        membersString = membersString.substring(1, membersString.length() - 1);
        String[] members = membersString.split(","); 
        List<Long> outerMemberIds = new ArrayList<Long>();
        for (int i = 0; i < members.length; i++) {
            if ("outer".equals(members[i]) && members[i-1].startsWith("w")) {
                outerMemberIds.add(Long.parseLong(members[i - 1].substring(1)));
                //OsmApiRoot memberData = this.osmApiService.readElement(outerMemberId);
            }
        }
        // For now support only relation with just one outer member
        if (outerMemberIds.size() == 1) {
            result = outerMemberIds.get(0);
            LOGGER.info("For multipolygon relation with id=" + relationId + ", outer member with id=[" + result +"] has been found");
        } 
        // Else we just return the negative relation ID
        else {
            result = - relationId;
            LOGGER.warn("For multipolygon relation with id=" + relationId + ", no outer member has been found (members are " + membersString + ")");
        }
        return result;
    }
    
    private Long[] findBuildingIDsByGeometry(String geometry) {
        LOGGER.info("Looking in PostGIS for buildings containing GEOMETRY=" + geometry + ":");
        // TODO Test PostGIS request with intersection operator
        String query = "select osm_id from planet_osm_polygon "
                + "where building <> '' and  ST_Intersects(way, ST_GeomFromText('" + geometry + "', 4326));";
        return this.osmPostgisService.findElementIdsByQuery(query);
    }

    private Long[]  findBuildingIDsByLatLon(double lon, double lat) {
        //List<Long> result = new ArrayList<Long>();
        LOGGER.info("Looking in PostGIS for buildings containing POINT(" + lon + ", " + lat + "):");
        String query = "select osm_id from planet_osm_polygon "
                + "where building <> '' and  ST_Contains(way, ST_Transform(ST_GeomFromText('POINT(" + lon + " " + lat
                + ")', 4326), 900913));";
        return this.osmPostgisService.findElementIdsByQuery(query);
    }
        
}
