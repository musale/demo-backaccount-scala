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
aggregate:
	@sbt coverageAggregate

.PHONY: codacy
codacy:
	@sbt codacyCoverage
