FROM mcr.microsoft.com/playwright:v1.64.0-noble
RUN apt-get update && apt-get install fonts-noto-color-emoji -y \
    && apt-get clean && rm -rf /var/lib/apt/lists/*
WORKDIR /e2e
COPY package.json package-lock.json ./
RUN npm ci
CMD ["npx", "playwright", "test"]
