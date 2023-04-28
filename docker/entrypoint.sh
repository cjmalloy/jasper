java -Djava.security.egd=file:/dev/./urandom "-Xmx${JASPER_HEAP:-512m}" "-Xms${JASPER_HEAP:-512m}" org.springframework.boot.loader.JarLauncher
