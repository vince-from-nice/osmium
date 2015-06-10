package org.openstreetmap.osmaxil.model.xml.osm;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

public class OsmApiWay {
    
    @XmlAttribute
    public Long id;
    
    @XmlAttribute
    public int version;
    
    @XmlAttribute
    public long changeset;
    
    @XmlAttribute
    public String user;
    
    @XmlAttribute
    public String visible;
    
    @XmlAttribute
    public int uid;
    
    @XmlAttribute
    public String timestamp;
    
    @XmlAttribute
    public String action;
    
    // Specific attributes

    @XmlElement (name="nd")
    public List<OsmApiNd> nds = new ArrayList<>();;
    
    
    @XmlElement (name="tag")
    public List<OsmApiTag> tags = new ArrayList<>();;
}
