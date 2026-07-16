# Notas de implementación v0.1.22

## Cristal líquido (beta)

Se prepara como estilo independiente del tema:

- Tema:
  - Noche
  - Día

- Estilo:
  - Sólido
  - Cristal líquido

- Calidad:
  - FULL
  - REDUCED
  - FALLBACK

### Reglas seguras

- No desenfocar continuamente GeckoView.
- Usar blur nativo solo cuando API >= 31.
- Si el blur no está disponible:
  - panel translúcido,
  - borde suave,
  - brillo superior,
  - mayor opacidad para legibilidad.

## Selección de texto

Los campos identificados como:
- search
- address
- url
- query

reciben, cuando es posible:
- textIsSelectable=true
- longClickable=true
- hapticFeedbackEnabled=true

## Permisos

Se añaden permisos base para:
- RECORD_AUDIO
- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO
- READ_MEDIA_AUDIO
- READ_EXTERNAL_STORAGE (hasta API 32)
