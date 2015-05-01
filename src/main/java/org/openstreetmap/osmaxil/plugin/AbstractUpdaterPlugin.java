package org.openstreetmap.osmaxil.plugin;

import org.openstreetmap.osmaxil.model.AbstractElement;
import org.openstreetmap.osmaxil.model.AbstractImport;
import org.openstreetmap.osmaxil.model.api.OsmApiRoot;

public abstract class AbstractUpdaterPlugin<Element extends AbstractElement, Import extends AbstractImport>
        extends AbstractPlugin<Element, Import> {

    abstract public String[] getUpdatableTagNames();

    abstract public boolean isElementTagUpdatable(Element element, String tagName);

    abstract public boolean updateElementTag(Element element, String tagName);

    public Element instanciateElement(long osmId, long relationId, OsmApiRoot data) {
        Element element = super.instanciateElement(osmId, relationId, data);
        for (String tagName : this.getUpdatableTagNames()) {
            element.getOriginalValuesByTagNames().put(tagName, element.getTagValue(tagName));
        }
        return element;
    }

}
