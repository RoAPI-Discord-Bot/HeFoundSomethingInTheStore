import os
import math
from PIL import Image, ImageDraw

def generate_adaptive_icons(source_image_path, app_res_dir):
    if not os.path.exists(source_image_path):
        print(f"Source image not found: {source_image_path}")
        return

    # Density configurations: (density_folder, legacy_size, adaptive_fg_size)
    densities = [
        ("mipmap-mdpi", 48, 108),
        ("mipmap-hdpi", 72, 162),
        ("mipmap-xhdpi", 96, 216),
        ("mipmap-xxhdpi", 144, 324),
        ("mipmap-xxxhdpi", 192, 432),
    ]

    img = Image.open(source_image_path).convert("RGBA")
    src_w, src_h = img.size

    for folder_name, legacy_sz, fg_sz in densities:
        folder_path = os.path.join(app_res_dir, folder_name)
        os.makedirs(folder_path, exist_ok=True)

        # 1. Generate Adaptive Foreground (fg_sz x fg_sz)
        # Android safe zone is 66dp diameter in 108dp canvas (~61.1% of size)
        safe_diameter = int(fg_sz * 0.611)
        
        # Calculate aspect ratio preserving scale to fit inside safe zone
        scale = min(safe_diameter / src_w, safe_diameter / src_h)
        new_w = max(1, int(src_w * scale))
        new_h = max(1, int(src_h * scale))

        resized_img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)

        # Create transparent canvas for adaptive foreground
        fg_canvas = Image.new("RGBA", (fg_sz, fg_sz), (0, 0, 0, 0))
        offset_x = (fg_sz - new_w) // 2
        offset_y = (fg_sz - new_h) // 2
        fg_canvas.paste(resized_img, (offset_x, offset_y), resized_img)

        fg_path = os.path.join(folder_path, "ic_launcher_foreground.png")
        fg_canvas.save(fg_path, "PNG")

        # 2. Generate Legacy Square Icon (legacy_sz x legacy_sz)
        # Create dark background circle/rounded rect for legacy icon
        legacy_canvas = Image.new("RGBA", (legacy_sz, legacy_sz), (15, 15, 20, 255))
        leg_scale = min(legacy_sz / src_w, legacy_sz / src_h)
        leg_w = max(1, int(src_w * leg_scale))
        leg_h = max(1, int(src_h * leg_scale))
        leg_resized = img.resize((leg_w, leg_h), Image.Resampling.LANCZOS)
        leg_off_x = (legacy_sz - leg_w) // 2
        leg_off_y = (legacy_sz - leg_h) // 2
        legacy_canvas.paste(leg_resized, (leg_off_x, leg_off_y), leg_resized)

        legacy_path = os.path.join(folder_path, "ic_launcher.png")
        legacy_canvas.save(legacy_path, "PNG")

        # 3. Generate Legacy Round Icon (circular mask)
        round_canvas = Image.new("RGBA", (legacy_sz, legacy_sz), (0, 0, 0, 0))
        mask = Image.new("L", (legacy_sz, legacy_sz), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, legacy_sz, legacy_sz), fill=255)
        
        round_canvas.paste(legacy_canvas, (0, 0), mask)
        round_path = os.path.join(folder_path, "ic_launcher_round.png")
        round_canvas.save(round_path, "PNG")

        print(f"Generated icons in {folder_name} (fg: {fg_sz}x{fg_sz}, legacy: {legacy_sz}x{legacy_sz})")

if __name__ == "__main__":
    source_img = r"C:\Users\rucki\.gemini\antigravity\brain\f644f0c5-f278-45aa-a3f7-6b9d693e67ff\media__1785198075411.jpg"
    target_res = os.path.abspath(r"app\src\main\res")
    generate_adaptive_icons(source_img, target_res)
