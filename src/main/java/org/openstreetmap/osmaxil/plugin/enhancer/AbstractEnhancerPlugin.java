package org.openstreetmap.osmaxil.plugin.enhancer;

import java.util.Collection;
import java.util.List;

import org.openstreetmap.osmaxil.model.AbstractElement;
import org.openstreetmap.osmaxil.model.AbstractImport;
import org.openstreetmap.osmaxil.model.ElementType;
import org.openstreetmap.osmaxil.model.xml.osm.OsmXmlRoot;
import org.openstreetmap.osmaxil.plugin.updater.AbstractUpdaterPlugin;

public abstract class AbstractEnhancerPlugin<ELEMENT extends AbstractElement, IMPORT extends AbstractImport>
		extends AbstractUpdaterPlugin<ELEMENT, IMPORT> {

	/**
	 * Existing elements which are inside the filtering areas.
	 */
	protected List<ELEMENT> targetedElement;
	
	protected int counterForUpdatableElements = 0;
	
	protected int limitForMatchedElements = 0;

	abstract protected List<IMPORT> findMatchingImports(ELEMENT element, int srid);

	abstract protected List<ELEMENT> getTargetedElements();
	
	// =========================================================================
	// Public methods
	// =========================================================================

	@Override
	public void process() {
		// Load IDs of all targeted elements
		LOGGER.info("Looking in PostGIS for existing elements which are respecting the filtering areas");
		this.targetedElement = this.getTargetedElements();
		int i = 1;
		// For each targeted element, 
		for (ELEMENT element : this.targetedElement) {
			LOGGER.info(LOG_SEPARATOR);
			if (element.getOsmId() == null || element.getOsmId() == 0) {
				LOGGER.warn("Element is null, skipping it...");
				break;
			}
			// Bind it with its matching elements
	        LOGGER.info("Find matching imports for element #" + element.getOsmId() + " <" + i++ + ">");
			this.associateImportsWithElements(element);
			// If the element has at least one matching import 
			if (!element.getMatchingImports().isEmpty()) {
	        	// Compute its matching score
	            LOGGER.info("Computing matching score for element #" + element.getOsmId());
	            this.computeMatchingScores(element);
	        	// Update element with data from OSM API only if its matching score is ok
	            if (element.getMatchingScore() >= this.getMinimalMatchingScore()) {
		        	LOGGER.info("Update data of element #" + element.getOsmId() + " from OSM API");
		        	this.updateElementDataFromAPI(element);
		        	counterForUpdatableElements++;
	            }
			}			
            // Check limit (useful for debug) 
			if (limitForMatchedElements > 0 && this.matchedElements.size() == limitForMatchedElements) {
				break;
			}
		}
		LOGGER.info(LOG_SEPARATOR);
	}

    @Override
    public void displayProcessingStatistics() {
        LOGGER_FOR_STATS.info("=== Processing statistics ===");
        LOGGER_FOR_STATS.info("Total of elements which have been targeted: " + this.targetedElement.size());
        LOGGER_FOR_STATS.info("Total of elements which have at least one matching imports: " + this.matchedElements.size());
        LOGGER_FOR_STATS.info("Total of matching imports: " + this.counterForMatchedImports);
        LOGGER_FOR_STATS.info("Total of matched elements: " + this.matchedElements.size());
		LOGGER_FOR_STATS.info("Average of matching imports for each elements: "
				+ (this.matchedElements.size() > 0 ? this.counterForMatchedImports / this.matchedElements.size() : "0"));
        this.scoringStatsGenerator.displayStatsByMatchingScore((Collection<AbstractElement>) matchedElements.values());
        LOGGER_FOR_STATS.info("Minimum matching score is: " + this.getMinimalMatchingScore());
        LOGGER_FOR_STATS.info("Total of updatable elements: " + this.counterForUpdatableElements);
    }

	// =========================================================================
	// Private methods
	// =========================================================================
	
	private void associateImportsWithElements(ELEMENT element) {
        // Find matching imports
        List<IMPORT> matchingImports = this.findMatchingImports(element, this.osmPostgis.getSrid());
        if (matchingImports.size() > 0) {
        	this.matchedElements.put(element.getOsmId(), element);
        }
        // Bind all the imports to the targeted element
        for (IMPORT imp : matchingImports) {
            element.getMatchingImports().add(imp);
            imp.setMatchingElement(element);
		}
        StringBuilder sb = new StringBuilder("Matching imports are : [ ");
        for (AbstractImport i : element.getMatchingImports()) {
            sb.append(i.getId() + " ");
        }
        LOGGER.info(sb.append("]").toString());
	}
	
	private void updateElementDataFromAPI(ELEMENT element) {
//		long osmId = (element.getOsmId() > 0 ? element.getOsmId() : - element.getOsmId());
//		ElementType elementType = (element.getOsmId() > 0 ? ElementType.Way : ElementType.Relation);
//		element.setOsmId((element.getRelationId() == null ? element.getOsmId() : element.getRelationId()));
		// TODO store type into element and use it everywhere
        OsmXmlRoot apiData = this.osmStandardApi.readElement(element.getOsmId(), ElementType.Way);
        if (apiData == null) {
            LOGGER.warn("Unable to fetch data from OSM API for element#" + element.getOsmId());
        } else {
	        element.setApiData(apiData);        
	        // Store original values for each updatable tag
	        for (String tagName : this.getUpdatableTagNames()) {
	            element.getOriginalValuesByTagNames().put(tagName, element.getTagValue(tagName));
	        }
        }
	}
}
