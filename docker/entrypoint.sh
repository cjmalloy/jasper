
if [ -z "$(ls -A /cr 2> /dev/null)" ]; then
  echo "Creating checkpoint..."
	java \
		-Djava.security.egd=file:/dev/./urandom \
		-Dspring.context.checkpoint=onRefresh \
		-XX:CRaCCheckpointTo=/cr \
		-XX:CRaCMinPid=128 \
		"-Xmx${JASPER_HEAP:-512m}" \
		"-Xms${JASPER_HEAP:-512m}" \
		-XX:+UseStringDeduplication \
		-XX:+UseCompactObjectHeaders \
		org.springframework.boot.loader.launch.JarLauncher
fi

echo "Restoring the application..."
java \
	-Djava.security.egd=file:/dev/./urandom \
	-XX:CRaCRestoreFrom=/cr \
	"-Xmx${JASPER_HEAP:-512m}" \
	"-Xms${JASPER_HEAP:-512m}" \
	-XX:+UseStringDeduplication \
	-XX:+UseCompactObjectHeaders \
	org.springframework.boot.loader.launch.JarLauncher
