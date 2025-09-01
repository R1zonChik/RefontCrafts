# RefontCrafts

Лёгкий плагин для Paper 1.16.5–1.21.x:
- GUI для shapeless и рецептов наковальни (с ценой XP)
- Вещи из GUI всегда возвращаются при сохранении
- Логи плагина летят в чат игрокам с refontcrafts.admin
- Конфиг в простом формате: "MATERIAL:AMOUNT"

Ванильное ограничение: shapeless-рецепт — максимум 9 ингредиентов (каждый слот = 1).

## Установка
1) Помести .jar в папку `plugins/`
2) Перезапусти сервер

## Команды
- /rcrafts recipe — редактор shapeless
- /rcrafts anvil — редактор наковальни
- /rcrafts reload — перезагрузка конфига и рецептов
Алиасы: /rc, /refontcrafts

## Права
- refontcrafts.use — доступ (default: true)
- refontcrafts.admin — админ + логи в чат (default: op)

## Полный config.yml
```yaml
# ========== RefontCrafts ==========
# Создатель: https://t.me/orythix
# Здесь настройки и рецепты. Лёгкий формат без сериализации ItemStack.

settings:
  prefix: "§x§2§5§A§F§F§1R§x§2§2§A§8§F§2e§x§1§E§A§1§F§4f§x§1§B§9§B§F§5o§x§1§8§9§4§F§6n§x§1§4§8§D§F§7t§x§1§1§8§6§F§9C§x§0§D§7§F§F§Ar§x§0§A§7§8§F§Ba§x§0§7§7§2§F§Cf§x§0§3§6§B§F§Et§x§0§0§6§4§F§Fs &8»&7 "
  titles:
    recipe: "§x§2§5§A§F§F§1С§x§2§3§A§A§F§2о§x§2§0§A§5§F§3з§x§1§E§A§0§F§4д§x§1§B§9§B§F§5а§x§1§9§9§6§F§6н§x§1§6§9§1§F§7и§x§1§4§8§C§F§8е §x§0§F§8§2§F§9р§x§0§C§7§D§F§Aе§x§0§A§7§8§F§Bц§x§0§7§7§3§F§Cе§x§0§5§6§E§F§Dп§x§0§2§6§9§F§Eт§x§0§0§6§4§F§Fа"
    anvil: "§x§2§5§A§F§F§1Р§x§2§3§A§B§F§2е§x§2§1§A§7§F§3ц§x§1§F§A§3§F§3е§x§1§D§9§E§F§4п§x§1§B§9§A§F§5т §x§1§7§9§2§F§6в §x§1§3§8§A§F§8н§x§1§0§8§5§F§9а§x§0§E§8§1§F§Aк§x§0§C§7§D§F§Aо§x§0§A§7§9§F§Bв§x§0§8§7§5§F§Cа§x§0§6§7§1§F§Dл§x§0§4§6§C§F§Dь§x§0§2§6§8§F§Eн§x§0§0§6§4§F§Fю"
  exact_meta_match: false
  take_back_on_close: true
  default_anvil_cost: 0

recipes:
  shapeless:
    demo_trident:
      ingredients:
        - "BONE:1"
        - "IRON_INGOT:1"
        - "STICK:1"
      result: "TRIDENT:1"
  anvil:
    demo:
      left: "EMERALD:1"
      right: "EMERALD:1"
      result: "SEA_LANTERN:1"
      cost: 1
