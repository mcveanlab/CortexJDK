FROM continuumio/miniconda3

MAINTAINER Kiran V Garimella

# copy other resources
COPY ./environment.yml /

# install conda packages
RUN conda env create -f /environment.yml && conda clean -a
ENV PATH=/opt/conda/envs/corticall/bin/:/root/google-cloud-sdk/bin/:${PATH}

# install packages
RUN apt update
RUN apt-get install -y --no-install-recommends curl git \
    && rm -rf /var/lib/apt/lists/*

# install gsutil
RUN curl https://sdk.cloud.google.com | bash

# install Corticall
COPY corticall.jar /usr/local/bin/corticall.jar
