# Osmaxil #

Osmaxil is program written in Java which allow automatic data imports to the OpenStreetMap database.

It is designed as an expandable framework with plugins which can handle different types of OSM elements.

For now it's focused on building updates and there's currently 2 plugins available :
* OpenDataParis (http://opendata.paris.fr)
* PSS database (http://www.pss-archi.eu)

These 2 plugins update existing buildings in the OSM database by setting their height and/or number of levels tag(s) value(s).

## How to run ##

### Prerequesites ###

In order to run the program you need to have :
* Java 6 or later
* PostGIS
* Osm2pgsql

### Load local PostGIS ###

The program use a local PostGIS database in order to match imports with existing OSM elements.

So before to launch the program you need to populate your local PostGIS instance with OSM data related to the area you wan to update.

You can have a look to the file misc/setup_postgis.sh as example.

### Modify settings ###

All the settings are located in src/main/resources/settings.properties.

There's various settings such as local PostGIS or OSM API connection as well special settings for the plugins.

Plugins settings include for example which changeset source label and comment to use, or minimal matching score (see the section "How it works"). 

You also need to create you own password.properties file in src/main/resources which contains you private passwords (for OSM API and local PostGIS connection).

### Launch it !! ###

Once you data are loaded into your local PostGIS and your settings are well defined, you just have to launch the main class org.openstreetmap.osmaxil.Application.java. 

There's no special argument for now.

## How it works ##

The process is divided in separate phases :
* Imports loading
* Element processing
* Element synchronization
* Statistics generation

### Imports loading ###

That phase is implemented by the class named services.ImportLoader.

It load all imports from the source whose type depends of the actived plugin (for example the OpenDataParis plugin use a CSV file).

For each loaded import, the program looks for matching OSM element. The notion of "matching" depends on the plugins but typically it's based on the geographic location. For example, the OpenDataParis plugin looks for OSM buildings which *contains* (PostGIS function *ST_Contains()*) the coordinates of the imports.  

At the end of that phase all matching OSM elements are loaded into a Map (see the ElementCache class) and they are linked to their matching imports.

### Element processing ###

That phase is implemented by the class named services.ElementProcessor.

It process all matching OSM elements by setting matching score for all their relative imports. 

The method to calculate a matching score for each import depend on the actived plugin. For example the OpenDataParis plugin defines scores by calculating the ratio between the OSM building surface and the import building surface (ratio is a float between 0.0 and 1.0).

At the end the best matching import can be determined for each OSM element.

There's 2 ways for that : the old basic one and the new one which is more complex but more efficient.

The old way was just looking for each OSM element which matchint import was the best one, ie. the one with the best matching score.

The new way is more complex : first all matching imports of the OSM element are regrouped by their tag value into import lists. Then for each tag value a *total* matching score is calculated by accumulating matching score of each import of the list. 

Why to do that ? 

Let's consider a OSM building which is matched with 4 imported buildings :
- import building A has 8 levels and a matching score of 0.42
- import building B has 8 levels and a matching score of 0.35
- import building C has 5 levels and a matching score of 0.15
- import building C has 0 levels and a matching score of 0.08

With the old basic method the best matching score for the building is 0.42 (= score of the building A).
With the new complex method the best matching score for the building is 0.77 (= score of A + score of B).


### Element synchronization ###

That phase is implemented by the class named services.ElementSyncrhonizer.

It eventually writes OSM elements to the OSM API. For each matching OSM elements it checks if the matching score is enough (the minimum matching score is defined for each plugin in the settings.properties file).

If the matching score is enough, it tries to updates one or more tag values only. Depending on the actived plugin, the tag update can be done only if the tag hasn't an original value yet (it's the case with the OpenDataParis plugin). That way, the program will not destroy tag value which has been already insterted by other OSM contributors, it update elements which were *virgin* only.

### Statistics generation ###

That phase is implemented by the class named services.StatsGenerator.

It crashes various statistics on the stdout such as :
* Number of matched elements
* Number of updatable elements
* Number of updated elements

It also displays these statistics by matching score ranges.


## How to contribute ##

The source code is available on GitHub : https://github.com/vince-from-nice/osmaxil

Any suggestions or pull requests are welcome :)

