import SwiftUI

/// Główny ekran: Start albo otwarta karta, a na dole pasek programów jak pasek po lewej w LivePL na Windows.
struct Glowny: View {
    @EnvironmentObject private var p: Przegladarka
    @Environment(\.scenePhase) private var faza

    var body: some View {
        ZStack {
            Kolor.tlo.ignoresSafeArea()
            if let karta = p.aktywna {
                WidokKarty(karta: karta)
                    .id(karta.id)
            } else {
                Rezyserka()
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            PasekProgramow()
        }
        // klawiatura nie przesuwa paska ani stron: strony same robią miejsce na pole tekstowe
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .sheet(isPresented: $p.pokazListeKart) {
            ListaKart().environmentObject(p)
        }
        .sheet(item: $p.dodawanaStrona) { strona in
            DodajStrone(strona: strona).environmentObject(p)
        }
        .oknaMenuPrzegladarki()
        .onAppear { p.uruchom() }
        .onChange(of: faza) { _, nowa in
            if nowa == .active { p.zastosujMotyw() } else { p.zapiszSesje() }
        }
    }
}

/// Dolny pasek: Start, programy (z kropką, gdy są otwarte) i licznik kart. Same ikony, bez podpisów.
struct PasekProgramow: View {
    @EnvironmentObject private var p: Przegladarka

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Kolor.ramka).frame(height: 0.5)
            HStack(spacing: 2) {
                PrzyciskPaska(podpis: "Start", aktywny: p.aktywnaID == nil, otwarty: false) {
                    p.pokazRezyserke()
                } tresc: {
                    Image("Wstega").resizable().scaledToFit().frame(width: 36, height: 26)
                }

                Rectangle().fill(Kolor.ramka).frame(width: 0.5, height: 30)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 2) {
                        ForEach(p.programyNaPasku) { program in
                            let karta = p.karta(programu: program.id)
                            PrzyciskPaska(podpis: program.krotkaNazwa,
                                          aktywny: karta != nil && karta?.id == p.aktywnaID,
                                          otwarty: karta != nil) {
                                p.otworz(program)
                            } tresc: {
                                IkonaProgramu(program: program, rozmiar: 26)
                            }
                            .contextMenu {
                                if let karta {
                                    Button(role: .destructive) { p.zamknij(karta) } label: {
                                        Label("Zamknij \(program.krotkaNazwa)", systemImage: "xmark")
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 2)
                }

                Rectangle().fill(Kolor.ramka).frame(width: 0.5, height: 30)

                PrzyciskPaska(podpis: "Karty", aktywny: false, otwarty: false) {
                    p.pokazListeKart = true
                } tresc: {
                    LicznikKart(liczba: p.karty.count)
                }
            }
            .padding(.horizontal, 6)
            .padding(.top, 5)
            .padding(.bottom, 3)
        }
        .background(.bar)
    }
}

struct PrzyciskPaska<Tresc: View>: View {
    let podpis: String
    let aktywny: Bool
    let otwarty: Bool
    let akcja: () -> Void
    @ViewBuilder let tresc: () -> Tresc

    // Same ikony, bez podpisów pod spodem; nazwa zostaje dla VoiceOver i jako podpowiedź przy wskaźniku na iPadzie.
    var body: some View {
        Button(action: akcja) {
            tresc()
                .frame(height: 28)
                .frame(minWidth: 50, minHeight: 44)
                .padding(.horizontal, 3)
                .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(aktywny ? Kolor.wcisniety : Color.clear))
                .overlay(alignment: .topTrailing) {
                    if otwarty && !aktywny {
                        Circle().fill(Kolor.czerwony).frame(width: 6, height: 6).offset(x: -6, y: 4)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(podpis)
        .accessibilityLabel(podpis)
        .accessibilityAddTraits(aktywny ? .isSelected : [])
    }
}

struct LicznikKart: View {
    let liczba: Int

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .stroke(Kolor.tekst, lineWidth: 1.6)
                .frame(width: 22, height: 22)
            Text(liczba > 99 ? "∞" : "\(liczba)")
                .font(.system(size: 11, weight: .bold).monospacedDigit())
                .foregroundStyle(Kolor.tekst)
        }
    }
}

/// Grafika programu (Poczta, Live Production, TVML) albo ikona systemowa.
struct IkonaProgramu: View {
    let program: Program
    var rozmiar: CGFloat = 24

    var body: some View {
        if let obrazek = program.obrazek {
            Image(obrazek)
                .resizable()
                .scaledToFit()
                .frame(width: program.szeroki == true ? rozmiar * 1.9 : rozmiar, height: rozmiar)
        } else {
            Image(systemName: program.symbol)
                .font(.system(size: rozmiar * 0.8))
                .foregroundStyle(Kolor.tekst)
                .frame(width: rozmiar, height: rozmiar)
        }
    }
}
