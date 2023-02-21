/*
* SchluterDitraHeatChild
*
* Description:
* This Hubitat driver is designed for use with a Schluter DITRA-HEAT Wifi thermostat.  This is the child driver representing a single
* thermostat under an account
*
* Preferences:
* None - on aprent
*
* Features List:
*   View data from Schluter DITRA-HEAT Wifi thermostats
* 
* Licensing:
* Copyright 2023 Marc Reyhner
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1.0 - Initial version
* 
* Thank you(s):
*   I used the Tesla PowerWall driver fron snell as a sample and skeleton for creating a HTTP based driver
*       https://community.hubitat.com/t/project-driver-for-connecting-to-your-tesla-devices/41375
*   API details learned from py-schluter library - https://github.com/prairieapps/py-schluter/
*/



metadata{
    definition ( name: "SchluterDitraHeatChild", namespace: "marcre", author: "Marc Reyhner", importUrl: "tbd" ) {
        capability "Thermostat"
    }
}

def installed() {
    sendEvent(name: 'supportedThermostatFanModes', value: ["\"auto\""] )
    sendEvent(name: 'supportedThermostatModes', value: ["\"heat\"", "\"off\""] )
}

def ProcessUpdate(thermostat) {
    Logging("Thermostat raw data ${thermostat}", 4)

    UpsertAttribute("temperature", celsiusToFahrenheit(thermostat.Temperature / 100), "F")
    UpsertAttribute("thermostatSetpoint", celsiusToFahrenheit(thermostat.SetPointTemp / 100), "F")
}

def UpsertAttribute( Variable, Value, Unit = null ){
    if( device.currentValue(Variable) != Value ){

        if( Unit != null ){
            Logging( "Event: ${ Variable } = ${ Value }${ Unit }", 2 )
            sendEvent( name: "${ Variable }", value: Value, unit: Unit )
        } else {
            Logging( "Event: ${ Variable } = ${ Value }", 1 )
            sendEvent( name: "${ Variable }", value: Value )
        }
    }
}

// Handles whether logging is enabled and thus what to put there.
def Logging( LogMessage, LogLevel ){
    
    logType = getParent().LogType
    
    // Add all messages as info logging
    if( ( LogLevel == 2 ) && ( logType != "None" ) ){
        log.info( "${ device.displayName } - ${ LogMessage }" )
    } else if( ( LogLevel == 3 ) && ( ( logType == "Debug" ) || ( logType == "Trace" ) ) ){
        log.debug( "${ device.displayName } - ${ LogMessage }" )
    } else if( ( LogLevel == 4 ) && ( logType == "Trace" ) ){
        log.trace( "${ device.displayName } - ${ LogMessage }" )
    } else if( LogLevel == 5 ){
        log.error( "${ device.displayName } - ${ LogMessage }" )
    }
}