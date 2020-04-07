# MapRoulette Routes

MapRoulette uses Play Frameworks injection routing, which essentially uses dependency injection to build all the routes based in the route files. Play does allow you to split these into multiple route files, however due to our usage of Play-Swagger we cannot, as Play-Swagger does not support creating your swagger documentation with multiple route files. So MapRoulette has built it's own structure to do this.

### How?

So the process that MapRoulette is very simple, basically as part of the compile process it will take all the api files that are supplied in the [build.sbt](../../build.sbt) file and then concatenate those files together prior to compilation, creating one large Routes files called generated.routes which is then referenced in the primary [routes](../routes) file.

**NOTE** 

It is important to note that it will only build the generated routes file if one does not exist. If the file does it exist it will not regenerate it. This is because in dev mode code can be recompiled while running the server which makes it very useful to simply modify your code and hit the endpoint again. If the routes file is regenerated it will cause a much large compilation due to multiple other files being required to be built even if the generated routes file did not change. 

There are 3 sbt tasks that help in managing your routes file:
1) generateRoutesFile - This builds the routes file if and only if the routes file does not exist. The compile task depends on this task.
2) deleteRoutesFile - This will simply delete the current generated routes file.
3) regenerateRoutesFile - A helper task that sequentially runs the `deleteRoutesFile` task first and then the `generateRoutesFile` task.

### Why?

Why not just use the one large file? Well this single large file was getting very large, close to 5000 lines. The reason is not particularly because there were a ton of API routes, but rather that the swagger documentation for it was getting very large. To make it more manageable we just create multiple api route files so that we can manage the building of our swagger documentation more effectively.

