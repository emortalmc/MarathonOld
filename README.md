# Marathon

## Setup
- Download the latest Marathon jar from [releases](https://github.com/EmortalMC/Marathon/releases/tag/latest) (make sure to download `Marathon-1.0.0-all.jar`)
- Run the jar using `java -Xmx256 -jar Marathon-1.0.0-all.jar`. This will generate some config files.
- Set `"defaultGame"` in `config.json` to `"marathon"` (So you end up with `"defaultGame": "marathon",`)
- Run the jar again.
- Enjoy!

### Using leaderboards
In order to use leaderboards and highscores, you must have a hosted MongoDB server. [MongoDB offers a free shared plan](https://www.mongodb.com/pricing)

Once you have a hosted MongoDB server, you can enable it in `marathon.json` and replace the `connectionString` using your MongoDB address