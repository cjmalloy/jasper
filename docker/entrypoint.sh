java \
	-Djava.security.egd=file:/dev/./urandom \
	"-Xmx${JASPER_HEAP:-512m}" \
	"-Xms${JASPER_HEAP:-512m}" \
	-XX:+UseStringDeduplication \
	-XX:+UseCompactObjectHeaders \
	org.springframework.boot.loader.launch.JarLauncher
