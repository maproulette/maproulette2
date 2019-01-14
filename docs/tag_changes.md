# OSM Changeset API

This API in MapRoulette allows users to submit tag changes to the server, and then MapRoulette will attempt to conflate those changes with current data in OpenStreetMap. This API is primarily for support for the feature in MapRoulette called Suggested Fixes. Suggested fixes allow challenges to be created that ask the user if a change is valid or not. And if it is then MapRoulette can submit those changes directly to OpenStreetMap without the user having to make edits in JOSM or iD. This document however focuses on the API that is used to make those calls and what is going on behind the scenes and explain the workflow to the user.

### Tag Changes

The Tag Changes, are JSON based delta changes that would then be applied to specific elements in the data. And as this is limited to only tag based changes, conflation is a little easier, however contains specific rules for what to do under what circumstances.

A TagChange object looks as follows:
```json
{
  "osmId":int,
  "osmType":OSMType,
  "updates":Map[String, String],
  "deletes":List[String],
  "version":Option[Int]
}
```

- osmId - This is the ID of the feature that you are modifying
- osmType - The type of object, either NODE, WAY or RELATION
- updates - The tag updates that you wish to make. A series of key value pairs, the conflation on the backend will figure out if the update is an update or a new tag. In the delta or osmchange responses it will show whether it is treating the tag as a new tag or an updated tag.
- deletes - A list of tag keys that you want to delete from the OSM feature.
- version - Optionally you can set an object version, so this would be what version of the object your changes are based on. Currently this is not used, however in the future the idea would be that if the version is not the same as the current version we would simply return a conflict and not even try to upload them.

A TagChangeSubmission is an object that you would submit when wanting to actually submit your changes to OpenStreetMap. This would go into the body of your request.
```json
{
  "comment":string,
  "changes":List[TagChange]
}
```

- comment - The comment that is going to be associated with this change.
- changes - A list of TagChanges, that is described above.

## API

### Testing Changes

```
POST /api/v2/change/tag/test?changeType=<delta|osmchange>
HEADER <BASIC AUTH> or <OAUTH 1.1a> for Authentication
BODY {
  ... // See TagChange, in the body you would simply provide an array of TagChanges
}
```
This API will allow you to test your changes. The response from the server will either be a set of delta changes or the OSMChange that would be submitted to the server (minus the changesetId) when you actually submit these changes.

### Submitting Changes

```
POST /api/v2/change/tag/submit
HEADER <BASIC AUTH> or <OAUTH 1.1a> for Authentication
BODY {
  ... // See TagChangeSubmission
}
```
This API will submit the changes to the server. The response will be the actual OSMChange that was successfully submitted. Any conflicts will be reported with a 409 Conflict HTTP response.
