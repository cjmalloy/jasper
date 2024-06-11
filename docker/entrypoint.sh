java \
	-Djava.security.egd=file:/dev/./urandom \
	"-Xmx${JASPER_HEAP:-512m}" \
	"-Xms${JASPER_HEAP:-512m}" \
	-XX:+UseStringDeduplication \
	org.springframework.boot.loader.JarLauncher
