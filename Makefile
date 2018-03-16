.PHONY: dev
dev:
	@sbt run

.PHONY: test
test:
	@sbt clean coverage test

.PHONY: report
report:
	@sbt coverageReport

.PHONY: aggregate
report:
	@sbt coverageAggregate

.PHONY: codacy
report:
	@sbt codacyCoverage
