### Sun Spot Temperature Aggregator

#### What it does

Aggregates temperature data in a Sun Spot sensor network. The data is sent periodically by a sensor. The aggregation of data was done primarily to spare the battery for such a message intensive task.

#### Building the app

I let you refer to the Sun Spot official doc to build the application and deploy to the Sun Spot devices.

#### How it works

It is a simple `HELLO/REPLY` protocol. The Host application broadcasts a `HELLO` request to hypothetical sensors in neighborhood and tries to build a tree. Each Spot attached to a father in the tree will recursively send a `HELLO` request to find further sensors in the network. If a sensor is already attached it will reply by a `TIED` message.

When the tree is formed each sensor begins to monitor the temperature in its environment. It periodically sends data to the father. The father waits for a majority of child sensors or waits for the timeout to expire and sends the weighted value to its father. Finally the Host application receives data of all sensors and calculates the correct value for the whole area

The program has a some fault tolerance mechanisms. If a sensor looses its father, it will try to attach itself again to the tree by broadcasting a `LOST` request. Sensors in the area will respond by Unicast to the lost sensor by a `HELLO` request to signal their presence in the area. The sensor replies to the first hypothetical father replying, ignoring other sensors.

For more informations about how the Host works, see the _SunSpotHostAggregator_ repository.