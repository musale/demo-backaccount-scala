.PHONY: dev
dev:
	@sbt run

.PHONY: test
test:
	@sbt clean coverage test

.PHONY: report
report:
	@sbt coverageReport