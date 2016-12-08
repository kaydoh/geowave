FROM centos:centos6

RUN yum -y install epel-release && \
    yum -y install asciidoc hatools createrepo rpm-build rpm2cpio tar unzip xmlto zip && \
	yum clean all
