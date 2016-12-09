class geowave::hbase {

  package { "geowave-${geowave::geowave_version}-${geowave::hadoop_vendor_version}-hbase":
    ensure => latest,
    tag    => 'geowave-package',
  }

  if !defined(Package["geowave-core"]) {
    package { "geowave-core":
      ensure => latest,
      tag    => 'geowave-package',
    }
  }

}
