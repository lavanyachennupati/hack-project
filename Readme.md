# API
api-read-cache has a REST-list API with routes registered and documented in [ApiReadCacheResource](https://github.com/lavanyachennupati/hack-project/blob/master/src/main/java/org/netflix/apiservice/resources/ApiReadCacheResource.java#L40)
and [ViewResource](https://github.com/lavanyachennupati/hack-project/blob/master/src/main/java/org/netflix/apiservice/resources/ViewResource.java#L39).

# Usage

### Build, Verify and Package
 ```
mvn clean package
 ```
 
### Build Docker image
```
docker build -t <image-name> .
```

### Run Docker image with environment variables
```
 docker run  -e GITHUB_API_TOKEN=<github_api_token> -e SERVER_PORT=<port> -p <port>:<port> <image-name>
```

 I built a  docker image `lchennupa/hack-projects:netflix-repos` and pushed it to the public registry
that will run the `api-read-cache` service when `GITHUB_API_TOKEN` environment variable is set to  a valid `github_api_token` 

```
docker run  -e GITHUB_API_TOKEN=<github_api_token> -e SERVER_PORT=8080 -p 8080:8080  lchennupa/hack-projects:netflix-repos
```


The following 4 endpoints that are to be cached are periodically polled every 60s and cached.
```
    "/",
    "/orgs/Netflix",
    "/orgs/Netflix/members",
    "/orgs/Netflix/repos"

```
The cache sizes, expiration, frequency of polling are all configurable in [api-read-cache.conf](https://github.com/lavanyachennupati/hack-project/blob/master/src/main/resources/api-read-cache.conf)

### Testing
Ran the given [api-test-suit](https://drive.google.com/file/d/1HPKYCMZ_fk2sYrWasOyDyoRl5LqH_gIE/view) that's provided.
As stated in the assignment most of the results the tests are asserted against are outdated.
But otherwise the results against the latest data on github are all accurate.

