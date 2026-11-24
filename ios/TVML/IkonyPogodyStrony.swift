// Wygenerowane z Panel-TVML public/assets/js/weather-icons.js (ikony pogody strony tvml.pl, 1:1).
// Nie edytować ręcznie: ios/narzedzia/ikony-pogody-ze-strony.js.
import Foundation

enum IkonyPogodyStrony {
    /// alias (klucz API, stan sceny, ikonka stacji) → (stan zestawu, wymuszona noc albo nil)
    static let aliasy: [String: (String, Bool?)] = [
        "sun": ("clear", nil),
        "cloud-sun": ("partly-cloudy", nil),
        "cloud": ("overcast", nil),
        "rain": ("rain", nil),
        "storm": ("storm", nil),
        "snow": ("snow", nil),
        "sleet": ("sleet", nil),
        "hail": ("hail", nil),
        "fog": ("fog", nil),
        "wind": ("wind", nil),
        "clear-night": ("clear", true),
        "partly-night": ("partly-cloudy", true),
        "sunny": ("clear", false),
        "partly-cloudy": ("partly-cloudy", nil),
        "cloudy": ("overcast", nil),
        "cloudy-night": ("overcast", true),
        "rainy-light": ("rain-light", nil),
        "rainy-moderate": ("rain", nil),
        "rainy-heavy": ("rain-heavy", nil),
        "stormy": ("storm", nil),
        "snowy": ("snow", nil),
        "windy": ("wind", nil),
        "foggy": ("fog", nil),
        "sunny-foggy": ("fog", false),
        "night-foggy": ("fog", true),
        "clear": ("clear", nil),
        "few": ("mostly-clear", nil),
        "change": ("partly-cloudy", nil),
        "light": ("rain-light", nil),
        "moderate": ("rain", nil),
        "heavy": ("rain-heavy", nil),
        "clear-fog": ("fog", nil),
        "few-fog": ("fog", nil),
        "change-fog": ("fog", nil),
        "cloud-fog": ("fog", nil),
        "wi-day-sunny": ("clear", false),
        "wi-night-clear": ("clear", true),
        "wi-day-cloudy-high": ("mostly-clear", false),
        "wi-night-alt-cloudy-high": ("mostly-clear", true),
        "wi-day-cloudy": ("partly-cloudy", false),
        "wi-night-alt-cloudy": ("partly-cloudy", true),
        "wi-cloud": ("overcast", nil),
        "wi-day-haze": ("fog", false),
        "wi-night-fog": ("fog", true),
        "wi-day-fog": ("fog", nil),
        "wi-fog": ("fog", nil),
        "wi-sprinkle": ("drizzle", nil),
        "wi-showers": ("rain", nil),
        "wi-rain": ("rain-heavy", nil),
        "wi-snow": ("snow", nil),
        "overcast": ("overcast", nil),
        "mostly-clear": ("mostly-clear", nil),
        "drizzle": ("drizzle", nil),
        "rain-light": ("rain-light", nil),
        "rain-heavy": ("rain-heavy", nil),
        "freezing-rain": ("freezing-rain", nil),
        "heat": ("heat", nil),
        "frost": ("frost", nil)
    ]
    static let stany: Set<String> = ["clear", "mostly-clear", "partly-cloudy", "overcast", "fog", "drizzle", "rain-light", "rain", "rain-heavy", "freezing-rain", "snow", "sleet", "hail", "storm", "wind", "heat", "frost"]

    /// Nazwa grafiki w Assets (IkonyPogody): pogoda-<stan>-<dzien|noc>, jak WeatherIcons.resolve na stronie (bez opisu i WMO).
    static func nazwa(_ klucz: String, noc: Bool) -> String {
        let k = klucz.trimmingCharacters(in: .whitespaces).lowercased()
        let domyslny: (String, Bool?) = stany.contains(k) ? (k, nil) : ("clear", nil)
        let a = aliasy[k] ?? domyslny
        let wNocy = a.1 ?? noc
        return "pogoda-\(a.0)-\(wNocy ? "noc" : "dzien")"
    }
}
