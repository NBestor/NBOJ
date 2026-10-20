ARG CPP17_BASE_IMAGE=gcc:13-bookworm
FROM ${CPP17_BASE_IMAGE}

LABEL org.opencontainers.image.title="Wenzhong OJ C++17 Runner"
LABEL org.opencontainers.image.description="C++17 contest runner for the online judge sandbox"

RUN useradd --create-home --shell /usr/sbin/nologin judge

WORKDIR /workspace

USER judge
