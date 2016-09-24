# Linked data project, started at Hack4DK [[https://rdf.solsort.com/icon-small.png]]


It is online at https://rdf.solsort.com/.

# Potential datasources in prioritised order

1. Natmus, http://samlinger.natmus.dk/api/all/_search?q=... http://samlinger.natmus.dk/api/assets/_mapping
2. Openplatform.dbc.dk
3. Europeana


Tasks:

- v0
  - Europeana data source
  - Better object presentation - semantic markup
  - Improve natmus data, ie. solvogn-bad

# Hack4Norden

Notes

- Intro
  - What
  - Why / my motivation / value
- Technical status
- Nordic challenge / criterias
  - value proposition (target group)
    - Institution
      - Potential authorative url for object
      - stepping stone towards spreading open data (connect to Europeana, wikidata, dbpedia, ...)
      - leads users to the institution, by exposing the data in a form that robots and search engines can read (unlike just an api)
    - Developer
      - single simple uniform endpoint/structure to access different datasets
    - User
      - able to search / get inspirations from different sources (federated search)
      - may find objects by normal web search
  - solve challenge make nordic GLAM content more findable and hackable
  - Innovation/creativity:
    - new product: Explorable/Crawlable NatMus collection, and Danish Bibliography, and connection of url and object
  - Nordic datasets:
    - now: All the Nordic Institutions that deliver data to Europeana, as well as Natmus, and Danish Bibliography
    - next: identify datasets in other nordic countries, 
  - Business/commercial potential. I do this for fun, but if we have to make the business case:
    - Making data more available is a service with a clear value, that can be offered to public and private institutions.
    - When data is collected in a similar format, it also leads to other services, such as semi-automatic enrichment and linking between the data, etc.
  - Nordic-wide issues: Making data better available, is relevant for many public and private institutions across all of the nordic countries.

- authorative url for data
- federated search
- gøre data lettere tilgængelige
- eksponere dem for søgemaskiner

# Object types


* Paths

- /object/
- ~/object/(natmus|ting|smk|dfi|..):id
- /search/natmus/query
- /search/ting/query

* Schemas:

- target schemas:
  - dublin core
  - schema.org
