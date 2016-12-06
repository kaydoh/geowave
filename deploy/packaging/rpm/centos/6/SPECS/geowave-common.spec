%define timestamp           %(date +%Y%m%d%H%M)
%define version             %{?_version}%{!?_version: UNKNOWN}
%define base_name           geowave
%define name                %{base_name}
%define versioned_app_name  %{base_name}-%{version}
%define buildroot           %{_topdir}/BUILDROOT/%{versioned_app_name}-root
%define installpriority     %{_priority} # Used by alternatives for concurrent version installs
%define __jar_repack        %{nil}
%define _rpmfilename        %%{ARCH}/%%{NAME}.%%{RELEASE}.%%{ARCH}.rpm

%define geowave_home           /usr/local/geowave
%define geowave_install        /usr/local/%{versioned_app_name}
%define geowave_accumulo_home  %{geowave_install}/accumulo
%define geowave_hbase_home     %{geowave_install}/hbase
%define geowave_docs_home      %{geowave_install}/docs
%define geowave_geoserver_home %{geowave_install}/geoserver
%define geowave_tools_home     %{geowave_install}/tools
%define geowave_plugins_home   %{geowave_tools_home}/plugins
%define geowave_geoserver_libs %{geowave_geoserver_home}/webapps/geoserver/WEB-INF/lib
%define geowave_geoserver_data %{geowave_geoserver_home}/data_dir
%define geowave_config         /etc/geowave

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Name:           %{base_name}
Version:        %{version}
Release:        %{timestamp}
BuildRoot:      %{buildroot}
BuildArch:      noarch
Summary:        GeoWave provides geospatial and temporal indexing on top of Accumulo and HBase
License:        Apache2
Group:          Applications/Internet
Source1:        bash_profile.sh
Source2:        site.tar.gz
Source3:        manpages.tar.gz
Source4:        puppet-scripts.tar.gz
BuildRequires:  unzip
BuildRequires:  zip
BuildRequires:  xmlto
BuildRequires:  asciidoc

%description
GeoWave provides geospatial and temporal indexing on top of Accumulo and HBase.

%install
# Copy system service files into place
mkdir -p %{buildroot}/etc/profile.d
cp %{SOURCE1} %{buildroot}/etc/profile.d/geowave.sh

# Copy documentation into place
mkdir -p %{buildroot}%{geowave_docs_home}
tar -xzf %{SOURCE2} -C %{buildroot}%{geowave_docs_home} --strip=1

# Copy man pages into place
mkdir -p %{buildroot}/usr/local/share/man/man1
tar -xvf %{SOURCE3} -C %{buildroot}/usr/local/share/man/man1
rm -rf %{buildroot}%{geowave_docs_home}/manpages
rm -f %{buildroot}%{geowave_docs_home}/*.pdfmarks

# Puppet scripts
mkdir -p %{buildroot}/etc/puppet/modules
tar -xzf %{SOURCE4} -C %{buildroot}/etc/puppet/modules


# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package        core
Summary:        GeoWave Core
Group:          Applications/Internet
Provides:       core = %{version}

%description core
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the GeoWave home directory and user account

%pre core
getent group geowave > /dev/null || /usr/sbin/groupadd -r geowave
getent passwd geowave > /dev/null || /usr/sbin/useradd --system --home /usr/local/geowave -g geowave geowave -c "GeoWave Application Account"

%postun core
if [ $1 -eq 0 ]; then
  /usr/sbin/userdel geowave
fi

%files core
%attr(644, root, root) /etc/profile.d/geowave.sh

%defattr(644, geowave, geowave, 755)
%dir %{geowave_config}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-docs
Summary:        GeoWave Documentation
Group:          Applications/Internet
Provides:       %{versioned_app_name}-docs = %{version}
Requires:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-docs
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the GeoWave documentation into the GeoWave directory

%files -n       %{versioned_app_name}-docs
%defattr(644, geowave, geowave, 755)
%doc %{geowave_docs_home}

%doc %defattr(644 root, root, 755)
/usr/local/share/man/man1/

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package        puppet
Summary:        GeoWave Puppet Scripts
Group:          Applications/Internet
Requires:       puppet

%description puppet
This package installs the geowave Puppet module to /etc/puppet/modules

%files puppet
%defattr(644, root, root, 755)
/etc/puppet/modules/geowave

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%changelog
* Fri Nov 23 2016 Rich Fecher <rfecher@gmail.com> - 0.9.3
- Add geowave-hbase
* Fri Jun 5 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.7-1
- Add external config file
* Fri May 22 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.7
- Use alternatives to support parallel version and vendor installs
- Replace geowave-ingest with geowave-tools
* Thu Jan 15 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-3
- Added man pages
* Mon Jan 5 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-2
- Added geowave-puppet rpm
* Fri Jan 2 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-1
- Added a helper script for geowave-ingest and bash command completion
* Wed Nov 19 2014 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2
- First packaging
