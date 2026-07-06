.PHONY: build image push

build:
	./gradlew :app:installDist --no-daemon -x test --build-cache

image: build
	docker build -t hnau256/llmchat:latest .

push: image
	docker buildx build --platform linux/amd64,linux/arm64 \
		-t hnau256/llmchat:latest --push .
