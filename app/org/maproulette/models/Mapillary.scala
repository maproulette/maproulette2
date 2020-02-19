// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

/**
  * @author mcuthbert
  */
case class MapillaryServerInfo(host: String, clientId: String, border: Double)

case class MapillaryImage(
    key: String,
    lat: Double,
    lon: Double,
    url_320: String,
    url_640: String,
    url_1024: String,
    url_2048: String
)
