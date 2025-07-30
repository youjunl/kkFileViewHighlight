FROM ubuntu:24.04
ARG TARGETARCH
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-8-jre tzdata locales libc-bin xfonts-utils fontconfig libxslt1.1 \
        cabextract wget \
        python3.11 && \

    # 切换至python3.11
    update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1
    python --version
    
    echo "Asia/Shanghai" > /etc/timezone && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    locale-gen zh_CN.UTF-8

RUN if [ "$TARGETARCH" = "arm64" ]; then \
        wget -O /tmp/LibreOffice.tar.gz "https://downloadarchive.documentfoundation.org/libreoffice/old/25.8.0.1/deb/aarch64/LibreOffice_25.8.0.1_Linux_aarch64_deb.tar.gz"; \
    else \
        wget -O /tmp/LibreOffice.tar.gz "https://downloadarchive.documentfoundation.org/libreoffice/old/25.8.0.1/deb/x86_64/LibreOffice_25.8.0.1_Linux_x86-64_deb.tar.gz"; \
    fi && \
    tar zxvf /tmp/LibreOffice.tar.gz -C /tmp && \
    cd /tmp/LibreOffice_25.8.0.1_Linux_*_deb/DEBS/ && \
    dpkg -i *.deb || true && \
    apt-get install -f -y

RUN echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" | debconf-set-selections && \
    echo "media-types media-types/install-mime.types select install" | debconf-set-selections && \
    apt-get install -y --no-install-recommends \
        -o Dpkg::Options::="--force-confdef" \
        -o Dpkg::Options::="--force-confold" \
        ttf-mscorefonts-installer \
        ttf-wqy-microhei \
        ttf-wqy-zenhei \
        xfonts-wqy

RUN apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/LibreOffice_25.8.0.1_Linux_*_deb*

ADD fonts/* /usr/share/fonts/chinese/

RUN cd /usr/share/fonts/chinese && \
    mkfontscale && \
    mkfontdir && \
    fc-cache -fv

ENV LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8
ENV KK_OFFICE_HOME=/opt/libreoffice25.8
