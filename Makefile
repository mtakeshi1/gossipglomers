SOURCES=$(shell find src -type f -name '*.scala')

build: target/gossipglomers-1.0-SNAPSHOT.jar

target/gossipglomers-1.0-SNAPSHOT.jar:	$(SOURCES)
	mvn clean install

clean:
	mvn clean
