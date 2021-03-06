/**
 *	Button Controller
 *
 *	Author: Brian Dahlem, based on SmartThings Button Controller
 *	Date: 2015-3-17
 */
definition(
    name: "VRCS Button Controller",
    namespace: "smartthings",
    author: "Brian Dahlem",
    description: "Control devices with buttons like Leviton VRCS4",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps@2x.png"
)

preferences {
	page(name: "selectButton")
	page(name: "configureButton1")
	page(name: "configureButton2")
	page(name: "configureButton3")
	page(name: "configureButton4")
}

def selectButton() {
	dynamicPage(name: "selectButton", title: "First, select your zwave controller", nextPage: "configureButton1", uninstall: configured()) {
		section {
			input "buttonDevice", "capability.button", title: "Controller", multiple: false, required: true
		}
        section ("If there is a built in switch, what device is it?") {
        	input "relayDevice", "capability.switch", title: "Built in switch", multiple: false, required: false
            input "relayAssociate", "bool", title: "Use switch 1 to control relay?"
        }
		
		section(title: "More options", hidden: hideOptionsSection(), hideable: true) {
			
			def timeLabel = timeIntervalLabel()

			href "timeIntervalInput", title: "Only during a certain time", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null

			input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

			input "modes", "mode", title: "Only when mode is", multiple: true, required: false
		}
	}
}

def configureButton1() {
	if (relayAssociate == true) {
    	dynamicPage(name: "configureButton1", title: "The first button will control ${relayDevice.displayName}",
			nextPage: "configureButton2", uninstall: configured(), null)
    }
    else {
		dynamicPage(name: "configureButton1", title: "Now let's decide how to use the first button...",
			nextPage: "configureButton2", uninstall: configured(), getButtonSections(1))
    }
}
def configureButton2() {
	dynamicPage(name: "configureButton2", title: "If you have a second button, what do you want it to do?",
		nextPage: "configureButton3", uninstall: configured(), getButtonSections(2))
}

def configureButton3() {
	dynamicPage(name: "configureButton3", title: "If you have a third button, what do you want it to do?",
		nextPage: "configureButton4", uninstall: configured(), getButtonSections(3))
}
def configureButton4() {
	dynamicPage(name: "configureButton4", title: "If you have a fouth button, what do you want it to do?",
		install: true, uninstall: true, getButtonSections(4))
}

def getButtonSections(buttonNumber) {
	return {
    	section(title: "Toggle these...", hidden: hideSection(buttonNumber, "toggle"), hideable: true) {
			input "lights_${buttonNumber}_toggle", "capability.switch", title: "switches:", multiple: true, required: false
			input "locks_${buttonNumber}_toggle", "capability.lock", title: "locks:", multiple: true, required: false
			input "sonos_${buttonNumber}_toggle", "capability.musicPlayer", title: "music players:", multiple: true, required: false
		}
    	section(title: "Turn on these...", hidden: hideSection(buttonNumber, "on"), hideable: true) {
			input "lights_${buttonNumber}_on", "capability.switch", title: "switches:", multiple: true, required: false
			input "sonos_${buttonNumber}_on", "capability.musicPlayer", title: "music players:", multiple: true, required: false
		}      
    	section(title: "Turn off these...", hidden: hideSection(buttonNumber, "off"), hideable: true) {
			input "lights_${buttonNumber}_off", "capability.switch", title: "switches:", multiple: true, required: false
			input "sonos_${buttonNumber}_off", "capability.musicPlayer", title: "music players:", multiple: true, required: false
		}
    	section(title: "Locks:", hidden: hideLocksSection(buttonNumber), hideable: true) {		
			input "locks_${buttonNumber}_unlock", "capability.lock", title: "Unlock these locks:", multiple: true, required: false
			input "locks_${buttonNumber}_lock", "capability.lock", title: "Lock these locks:", multiple: true, required: false
		}

		section("Modes") {
			input "mode_${buttonNumber}_on", "mode", title: "Activate these modes:", required: false
		}
		def phrases = location.helloHome?.getPhrases()*.label
		if (phrases) {
			section("Hello Home Actions") {
				log.trace phrases
				input "phrase_${buttonNumber}_on", "enum", title: "Activate these phrases:", required: false, options: phrases
			}
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	
	subscribe(buttonDevice, "button", buttonEvent)
    
    if (relayDevice) {
        log.debug "Associating ${relayDevice.deviceNetworkId}"
        if (relayAssociate == true) {
            buttonDevice.associateLoad(relayDevice.deviceNetworkId)
        }
        else {
            buttonDevice.associateLoad(0)
        }
    }
}

def configured() {
	return  buttonDevice || buttonConfigured(1) || buttonConfigured(2) || buttonConfigured(3) || buttonConfigured(4)
}

def buttonConfigured(idx) {
	return settings["lights_$idx_toggle"] ||
		settings["locks_$idx_toggle"] ||
		settings["sonos_$idx_toggle"] ||
		settings["mode_$idx_on"] ||
        settings["lights_$idx_on"] ||
		settings["locks_$idx_on"] ||
		settings["sonos_$idx_on"] ||
        settings["lights_$idx_off"] ||
		settings["locks_$idx_off"] ||
		settings["sonos_$idx_off"]
}

def buttonEvent(evt){
	log.debug "buttonEvent"
	if(allOk) {
		def buttonNumber = evt.value // why doesn't jsonData work? always returning [:]
		log.debug "buttonEvent: $evt.name - ($evt.data)"
		log.debug "button: $buttonNumber"
	
		def recentEvents = buttonDevice.eventsSince(new Date(now() - 3000)).findAll{it.value == evt.value && it.data == evt.data}
		log.debug "Found ${recentEvents.size()?:0} events in past 3 seconds"
        
		if(recentEvents.size <= 1){
			switch(buttonNumber) {
				case ~/.*1.*/:
					executeHandlers(1)
					break
				case ~/.*2.*/:
					executeHandlers(2)
					break
				case ~/.*3.*/:
					executeHandlers(3)
					break
				case ~/.*4.*/:
					executeHandlers(4)
					break
			}
		} else {
			log.debug "Found recent button press events for $buttonNumber with value $value"
		}
	}
    else {
    	log.debug "NotOK"
    }
}

def executeHandlers(buttonNumber) {
	log.debug "executeHandlers: $buttonNumber"

	def lights = find('lights', buttonNumber, "toggle")
	if (lights != null) toggle(lights)

	def locks = find('locks', buttonNumber, "toggle")
	if (locks != null) toggle(locks)

	def sonos = find('sonos', buttonNumber, "toggle")
	if (sonos != null) toggle(sonos)
    
	lights = find('lights', buttonNumber, "on")
	if (lights != null) flip(lights, "on")

	locks = find('locks', buttonNumber, "unlock")
	if (locks != null) flip(locks, "unlock")

	sonos = find('sonos', buttonNumber, "on")
	if (sonos != null) flip(sonos, "on")

	lights = find('lights', buttonNumber, "off")
	if (lights != null) flip(lights, "off")

	locks = find('locks', buttonNumber, "lock")
	if (locks != null) flip(locks, "lock")

	sonos = find('sonos', buttonNumber, "off")
	if (sonos != null) flip(sonos, "off")
    
	def mode = find('mode', buttonNumber, "on")
	if (mode != null) changeMode(mode)

	def phrase = find('phrase', buttonNumber, "on")
	if (phrase != null) location.helloHome.execute(phrase)
}

def find(type, buttonNumber, value) {
	def preferenceName = type + "_" + buttonNumber + "_" + value
	def pref = settings[preferenceName]
	if(pref != null) {
		log.debug "Found: $pref for $preferenceName"
	}

	return pref
}

def flip(devices, newState) {
	log.debug "flip: $devices = ${devices*.currentValue('switch')}"


	if (newState == "off") {
		devices.off()
	}
	else if (newState == "on") {
		devices.on()
	}
	else if (newState == "unlock") {
		devices.unlock()
	}
	else if (newState == "lock") {
		devices.lock()
	}
}

def toggle(devices) {
	log.debug "toggle: $devices = ${devices*.currentValue('switch')}"

	if (devices*.currentValue('switch').contains('on')) {
		devices.off()
	}
	else if (devices*.currentValue('switch').contains('off')) {
		devices.on()
	}
	else if (devices*.currentValue('lock').contains('locked')) {
		devices.unlock()
	}
	else if (devices*.currentValue('lock').contains('unlocked')) {
		devices.lock()
	}
	else {
		devices.on()
	}
}

def changeMode(mode) {
	log.debug "changeMode: $mode, location.mode = $location.mode, location.modes = $location.modes"

	if (location.mode != mode && location.modes?.find { it.name == mode }) {
		setLocationMode(mode)
	}
}

// execution filter methods
private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	log.trace "modeOk = $result"
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	log.trace "daysOk = $result"
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	log.trace "timeOk = $result"
	result
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private hideOptionsSection() {
	(starting || ending || days || modes) ? false : true
}

private hideSection(buttonNumber, action) {
	(find("lights", buttonNumber, action) || find("locks", buttonNumber, action) || find("sonos", buttonNumber, action)) ? false : true
}

private hideLocksSection(buttonNumber) {
	(find("lights", buttonNumber, "lock") || find("locks", buttonNumber, "unlock")) ? false : true
}

private timeIntervalLabel() {
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}

private integer(String s) {
	return Integer.parseInt(s)
}