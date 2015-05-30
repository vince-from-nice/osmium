package org.openstreetmap.osmaxil.step;

import javax.annotation.PreDestroy;

import org.apache.http.annotation.Obsolete;
import org.openstreetmap.osmaxil.dao.ElementStore;
import org.openstreetmap.osmaxil.dao.OsmXml;
import org.openstreetmap.osmaxil.model.AbstractElement;
import org.openstreetmap.osmaxil.model.xml.osm.OsmApiRoot;
import org.openstreetmap.osmaxil.plugin.remaker.AbstractRemakerPlugin;
import org.openstreetmap.osmaxil.plugin.updater.AbstractUpdaterPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SynchronizingStep extends AbstractStep {

    private long counterForMatchedElements;

    private long counterForUpdatedElements;
    
    private long counterForRemakedElements;

    @Autowired
    private ElementStore elementCache;
    
    @Value("${osmaxil.syncMode}")
    private String synchronizationMode;
    
    @Autowired
    private OsmXml osmXmlFile;

    //@PostConstruct
    public void init() {
        // Need to do an init on demand, when the synchronization phase is going to start to write elements.
        // If it would have been done on the Spring context initialization the first changeset could have become obsolete 
        // because changeset has an idle timeout of 1h and the previous phase (imports loading) could have taken more time.
        this.osmApiService.initForWriting(this.plugin.getChangesetSourceLabel(), this.plugin.getChangesetComment());
        this.synchronizationMode = this.synchronizationMode.trim();
    }
    
    @PreDestroy
    public void close() {
        LOGGER.info("=== Closing element synchronizer ===");
        LOGGER.info("Total of matched elements: " + this.counterForMatchedElements);
        LOGGER.info("Total of updated elements: " + this.counterForUpdatedElements);
        LOGGER.info("Total of remaked elements: " + this.counterForRemakedElements);
    }

    public void synchronizeElements() {
        this.init();
        LOGGER.info("=== Updating elements ===");
        LOGGER.info(LOG_SEPARATOR);
        for (AbstractElement element : this.elementCache.getElements().values()) {
            this.counterForMatchedElements++;
            try {
                synchronizeElement(element);
            } catch (java.lang.Exception e) {
                LOGGER.error("Synchronization of element " + element.getOsmId() + " has failed: ", e);
            }
            LOGGER.info(LOG_SEPARATOR);
        }
    }

    private void synchronizeElement(AbstractElement element) {
        if (element == null) {
            LOGGER.warn("Element is null, skipping it...");
            return;
        }
        LOGGER.info("Synchronizing element #" + this.counterForMatchedElements + ": " + element);
        // Check if its best matching score is enough
        if (element.getMatchingScore() < this.plugin.getMinimalMatchingScore()) {
            LOGGER.info("Element cannot be synchronized because its matching score is "
                    + element.getMatchingScore() + " (min=" + this.plugin.getMinimalMatchingScore() + ")");
            return;
        }
        // The synchronization process depends on the nature of the plugin
        if (this.plugin instanceof AbstractUpdaterPlugin) {
            // updateElementWithBestMatchingImport(element);
            updateElementWithBestAccumulatedImports(element);            
        } else if (this.plugin instanceof AbstractRemakerPlugin) {
            remakeElement(element);
        } else {
            LOGGER.warn("Unable to synchronize with plugin " + this.plugin); 
        }

    }
    
    /**
     * Perform the remaking process of an element:
     * All required changed have been calculated previously, just need to write in way which depends on the sync mode.
     */
    private void remakeElement(AbstractElement element) {
        boolean success = false;
        OsmApiRoot xml = ((AbstractRemakerPlugin) this.plugin).getXmlForRemaking(element.getOsmId());
        if (xml == null) {
            LOGGER.warn("Unable to sync element since its remaking data is null");
            return;
        }
        if ("api".equals(this.synchronizationMode)) {
           // TODO api writing for element remaking
        } else if ("gen".equals(this.synchronizationMode)) {
            success = this.osmXmlFile.writeToFile("id" + element.getOsmId(), xml);
        }
        if (success) {
            this.counterForRemakedElements++;
            element.setRemaked(true);
            LOGGER.debug("Ok element has been remaked");
        }
    }

    /**
     * Update element into OSM database with tag values which are coming from the import list which haves the best total
     * matching score. This method is based on the new matching method where matching imports have been regrouped by
     * their tag values.
     */
    private void updateElementWithBestAccumulatedImports(AbstractElement element) {
        boolean needToSync = false;
        AbstractUpdaterPlugin updaterPlugin = (AbstractUpdaterPlugin) this.plugin;
        for (String updatableTagName : updaterPlugin.getUpdatableTagNames()) {
            LOGGER.info("* Updating data for the tag " + updatableTagName);
            // Update tag value only if it is updatable
            if (updaterPlugin.isElementTagUpdatable(element, updatableTagName)) {
                boolean updated = updaterPlugin.updateElementTag(element, updatableTagName);
                if (updated) {
                    needToSync = true;
                }
            }
        }
        // Do the update sync only if needed
        if (needToSync) {
            boolean success = false;
            if ("api".equals(this.synchronizationMode)) {
                success = this.osmApiService.writeElement(element);
            } else if ("gen".equals(this.synchronizationMode)) {
                success = this.osmXmlFile.writeToFile("id" + element.getOsmId(), element.getApiData());
            }
            if (success) {
                this.counterForUpdatedElements++;
                element.setUpdated(true);
                LOGGER.debug("Ok element has been updated");
            }
        } else {
            LOGGER.info("Element cannot be updated (maybe original value(s) exist(s))");
        }
    }

}
