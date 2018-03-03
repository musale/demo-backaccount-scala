# Bank Account REST API

> Simple micro web service that mimics a “Bank Account”. Through this web service, one can query the balance, deposit money, and withdraw money.

## Getting Started

### Requirements

* Java 8
* Scala 2.12.x
* Play 2.6.x
* sbt 1.1.1

### Local development

* `$ make dev` will start a localhost server on port 9000.

### API Documentation

#### [GET] /api/v1/balance

Returns the current account balance

##### Request

`$ curl --verbose http://localhost:9000/api/v1/balance`

##### Response

```json
{
    "status": "OK",
    "message": "account balance is USD 140000.0",
    "code": 200
}
```

#### [POST] /api/v1/deposit

Allows you to make deposits to the account

##### Request

###### Params

```json
{
    "amount": "20000"
}
```

###### Sample call

`$ curl --verbose --header "Content-type: application/json" --request POST --data '{"amount": "20000"}' http://localhost:9000/api/v1/deposit`

##### Response

```json
{
    "status": "OK",
    "message": "new balance after deposit is USD 20000.0",
    "code": 201
}
```

##### Error responses

###### Deposit more than maximum deposit of 150k per day

```json
{
    "status": "error",
    "message": "exceeded maximum deposit amount of USD 150000.0 per day",
    "code": 403
}
```

###### Deposit more than 40k per transaction

```json
{
    "status": "error",
    "message": "exceeded maximum deposit of USD 40000.0 per transaction",
    "code": 403
}
```

###### Make more than 4 deposit transactions a day

```json
{
    "status": "error",
    "message": "exceeded maximum deposit frequency of USD 4 times per day",
    "code": 403
}
```

###### Make request with amount as alphanumerical

```json
{
    "amount": ["amount as00 should be a Number"]
}
```

#### [POST] /api/v1/withdraw

Allows you to make withdrawals from the account

##### Request

###### Params

```json
{
    "amount": "20000"
}
```

###### Sample call

`$ curl --verbose --header "Content-type: application/json" --request POST --data '{"amount": "20000"}' http://localhost:9000/api/v1/withdraw`

##### Response

```json
{
    "status": "OK",
    "message": "new balance after withdrawal of USD 2000.0 is USD 118000.0",
    "code": 201
}
```

##### Error responses

###### Withdraw more than maximum amount 50k per day

```json
{
    "status": "error",
    "message": "exceeded maximum withdrawa amount of USD 50000.0 per day",
    "code": 403
}
```

###### Withdraw more than 20k per transaction

```json
{
    "status": "error",
    "message": "exceeded maximum withdrawal of USD 20000.0 per transaction",
    "code": 403
}
```

###### Make more than 3 withdrawal transactions a day

```json
{
    "status": "error",
    "message": "exceeded maximum withdrawal frequency of 3 times per day",
    "code": 403
}
```

###### Make withrawal when balance is less than withdrawal amount

```json
{
    "status": "error",
    "message": "account balance is lower than withdrawal amount",
    "code": 403
}
```

###### Make request with amount as alphanumerical

```json
{
    "amount": ["amount as00 should be a Number"]
}
```

### Testing

Tests run alongside the [sbt-scoverage](https://github.com/scoverage/sbt-scoverage) plugin for coverage. Bitbucket pipelines run the CI/CD pipeline and the results can be seen [here.](https://bitbucket.org/musale/bank-account/addon/pipelines/home#!/)

* `$ make test` runs the tests with enabled coverage
* `$ make report` generates the coverage reports. The last coverage report run is stored [here](/public/files/report/index.html) as a html file. Summary of the last coverage numbers are shown below

![coverage report](/public/images/coverage.png "coverage report")