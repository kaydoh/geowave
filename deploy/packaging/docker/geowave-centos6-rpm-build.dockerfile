FROM centos:centos6

RUN yum -y install asciidoc hatools rpm-build rpm2cpio tar unzip xmlto zip && \
	yum clean all
